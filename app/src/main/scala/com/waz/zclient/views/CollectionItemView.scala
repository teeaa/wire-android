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
import android.graphics.Bitmap
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view.{HapticFeedbackConstants, LayoutInflater}
import android.widget.{FrameLayout, LinearLayout, TextView}
import com.waz.bitmap.BitmapUtils
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.service.assets.AssetService.BitmapResult
import com.waz.service.assets.AssetService.BitmapResult.BitmapLoaded
import com.waz.service.images.BitmapSignal
import com.waz.service.messages.MessageAndLikes
import com.waz.threading.Threading
import com.waz.ui.MemoryImageCache.BitmapRequest.Single
import com.waz.utils.events.{EventContext, Signal, SourceSignal}
import com.waz.zclient.messages.MessageView.MsgBindOptions
import com.waz.zclient.messages.MessageViewPart
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.main.conversation.views.AspectRatioImageView
import com.waz.zclient.utils.{ViewUtils, _}
import com.waz.zclient.views.ImageController.WireImage
import com.waz.zclient.{R, ViewHelper}
import org.threeten.bp.{LocalDateTime, ZoneId}

trait CollectionItemView extends ViewHelper {
  protected lazy val zms = inject[Signal[ZMessaging]]
  protected lazy val messageActions = inject[MessageActionsController]
  val messageData: SourceSignal[MessageData] = Signal()

  val messageAndLikes = zms.zip(messageData).flatMap{
    case (z, md) => Signal.future(z.msgAndLikes.combineWithLikes(md))
    case _ => Signal[MessageAndLikes]()
  }
  messageAndLikes.disableAutowiring()

  this.onLongClick {
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    messageAndLikes.currentValue.exists(messageActions.showDialog)
  }
}

class CollectionNormalItemView(context: Context, attrs: AttributeSet, style: Int) extends LinearLayout(context, attrs, style) with CollectionItemView {
  def this(context: Context, attrs: AttributeSet) = this(context, attrs, 0)
  def this(context: Context) = this(context, null, 0)

  inflate(R.layout.row_collection_item_view)

  val container: FrameLayout = ViewUtils.getView(this, R.id.fl__collections__content_layout)
  val messageTime: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__time)
  val messageUser: TextView = ViewUtils.getView(this, R.id.ttv__collection_item__user_name)

  messageData.flatMap(msg => zms.map(_.usersStorage).flatMap(_.signal(msg.userId))).on(Threading.Ui) {
    user => messageUser.setText(user.name)
  }

  messageData.on(Threading.Ui) {
    md => messageTime.setText(LocalDateTime.ofInstant(md.time, ZoneId.systemDefault()).toLocalDate.toString)
  }

  def inflateContent(contentId: Int) = LayoutInflater.from(getContext).inflate(contentId, container, true)

  def setMessageData(messageData: MessageData, content: Option[MessageContent]): Unit = {
    this.messageData ! messageData
    val messageViewPart = container.getChildAt(0).asInstanceOf[MessageViewPart]
    if (messageViewPart != null) {
      messageViewPart.set(messageData, content, CollectionNormalItemView.DefaultBindingOptions)
    }
  }
}

object CollectionNormalItemView{
  val DefaultBindingOptions = MsgBindOptions(0, isSelf = false, isLast = false, isLastSelf = false, isFirstUnread = false, listDimensions = Dim2.Empty, ConversationData.ConversationType.Unknown)
}

class CollectionImageView(context: Context) extends AspectRatioImageView(context) with CollectionItemView{
  messageData.on(Threading.Ui) { md =>
    val imageDrawable = new ImageAssetDrawable(Signal(WireImage(md.assetId)), scaleType = ImageAssetDrawable.ScaleType.CenterCrop)
    setImageDrawable(imageDrawable)
  }

  def setMessageData(messageData: MessageData, width: Int, color: Int) = {
    setAspectRatio(1)
    ViewUtils.setWidth(this, width)
    ViewUtils.setHeight(this, width)
    this.messageData ! messageData
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
