/**
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.waz.zclient.views

import android.content.Context
import android.net.Uri
import android.support.v7.widget.{CardView, RecyclerView}
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.View.OnClickListener
import android.webkit.URLUtil
import android.widget.TextView
import com.waz.api.impl.AccentColor
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.zclient.controllers.BrowserController
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.messages.parts.WebLinkPartView
import com.waz.zclient.messages.parts.assets.FileAssetPartView
import com.waz.zclient.messages.{MessageViewPart, MsgPart}
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.utils.ZTimeFormatter._
import com.waz.zclient.utils.{ViewUtils, _}
import com.waz.zclient.views.ImageController.WireImage
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.{LocalDateTime, ZoneId}

trait CollectionItemView extends ViewHelper {
  protected lazy val civZms = inject[Signal[ZMessaging]]
  protected lazy val messageActions = inject[MessageActionsController]
  val messageData: SourceSignal[MessageData] = Signal()

  val messageAndLikesResolver = civZms.zip(messageData).flatMap{
    case (z, md) => Signal.future(z.msgAndLikes.combineWithLikes(md))
    case _ => Signal[MessageAndLikes]()
  }
  messageAndLikesResolver.disableAutowiring()

  this.onLongClick {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    messageAndLikesResolver.currentValue.exists(messageActions.showDialog)
  }
}

trait CollectionNormalItemView extends CollectionItemView with MessageViewPart{
  lazy val messageTime: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__time)
  lazy val messageUser: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__user_name)

  var content = Option.empty[MessageContent]

  messageData.flatMap(msg => civZms.map(_.usersStorage).flatMap(_.signal(msg.userId))).on(Threading.Ui) {
    user =>
      messageUser.setText(user.name)
      messageUser.setTextColor(AccentColor(user.accent).getColor())
  }

  messageData.on(Threading.Ui) {
    md =>
      val timeStr = getSeparatorTime(getContext.getResources, LocalDateTime.now, DateConvertUtils.asLocalDateTime(md.time), DateFormat.is24HourFormat(getContext), ZoneId.systemDefault, true, false)
      messageTime.setText(timeStr)
  }

  messageAndLikesResolver.on(Threading.Ui) { mal => set(mal, content, CollectionNormalItemView.DefaultBindingOptions) }

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    this.content = content
    this.messageData ! messageData
  }
}

object CollectionNormalItemView{
  val DefaultBindingOptions = MsgBindOptions(0, isSelf = false, isLast = false, isLastSelf = false, isFirstUnread = false, listDimensions = Dim2.Empty, ConversationData.ConversationType.Unknown)
}

class CollectionImageView(context: Context) extends AspectRatioImageView(context) with CollectionItemView{
  val padding = getResources.getDimensionPixelSize(R.dimen.collections__image_padding)
  setCropToPadding(true)
  setPadding(padding, padding, padding, padding)

  messageData.on(Threading.Ui) { md =>
    val imageDrawable = new RoundedImageAssetDrawable(Signal(WireImage(md.assetId)), scaleType = ImageAssetDrawable.ScaleType.CenterCrop, cornerRadius = 10)
    setImageDrawable(imageDrawable)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    setAspectRatio(1)
    ViewUtils.setWidth(this, width)
    ViewUtils.setHeight(this, width)
    this.messageData ! messageData
  }
}

class CollectionWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends WebLinkPartView(context, attrs, style) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  override def inflate() = inflate(R.layout.collection_message_part_weblink_content)
}

class CollectionFileAssetPartView(context: Context, attrs: AttributeSet, style: Int) extends FileAssetPartView(context, attrs, style) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)
  override def layoutList = {
    case _: CollectionFileAssetPartView => R.layout.collection_message_file_asset_content
  }
}

class CollectionSimpleWebLinkPartView(context: Context, attrs: AttributeSet, style: Int) extends CardView(context: Context, attrs: AttributeSet, style: Int) with CollectionNormalItemView{
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  lazy val browser = inject[BrowserController]

  override val tpe: MsgPart = MsgPart.WebLink

  inflate(R.layout.collection_message_part_simple_link_content)

  lazy val urlTextView: TextView    = findById(R.id.ttv__row_conversation__link_preview__url)

  val urlText =
    message.map(msg => msg.content.find(c => URLUtil.isValidUrl(c.content)).map(_.content).getOrElse(msg.contentString))

  urlText.on(Threading.Ui){ urlTextView.setText}

  this.onClick {
    urlText.currentValue foreach { c => browser.openUrl(Uri.parse(c)) }
  }

}

abstract class CollectionItemViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view){

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    view.setMessageData(messageData, content)
  }

  def setMessageData(messageData: MessageData): Unit = {
    setMessageData(messageData, None)
  }
}

case class FileViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext) extends CollectionItemViewHolder(view)

case class LinkPreviewViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext) extends CollectionItemViewHolder(view) {
  override def setMessageData(messageData: MessageData): Unit = {
    val content = messageData.content.find(_.openGraph.nonEmpty)
    setMessageData(messageData, content)
  }
}

case class SimpleLinkViewHolder(view: CollectionNormalItemView)(implicit eventContext: EventContext) extends CollectionItemViewHolder(view)

case class CollectionImageViewHolder(view: CollectionImageView, listener: OnClickListener)(implicit eventContext: EventContext) extends RecyclerView.ViewHolder(view) {
  view.setOnClickListener(listener)

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    view.setMessageData(messageData, width, color)
  }
}
