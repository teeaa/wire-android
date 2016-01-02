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
package com.waz.zclient.conversation

import android.app.ProgressDialog
import android.content.{Context, DialogInterface}
import android.database.DataSetObserver
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.Toolbar
import android.text.format.Formatter
import android.util.AttributeSet
import android.view.View.OnClickListener
import android.view._
import android.widget._
import com.waz.api.ConversationsList.ConversationsListState
import com.waz.api._
import com.waz.model.{AssetData, AssetType, MessageData, MessageId}
import com.waz.threading.Threading
import com.waz.utils.events.{Signal, SourceSignal}
import com.waz.zclient.controllers.AssetsController
import com.waz.zclient.controllers.global.AccentColorController
import com.waz.zclient.core.stores.conversation.{ConversationChangeRequester, ConversationStoreObserver}
import com.waz.zclient.messages.MessagesController
import com.waz.zclient.pages.BaseDialogFragment
import com.waz.zclient.ui.text.TypefaceTextView
import com.waz.zclient.ui.views.CursorIconButton
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}


class ShareToMultipleFragment extends BaseDialogFragment[ShareToMultipleFragment.Container] with FragmentHelper with OnBackPressedListener {

  lazy val assetsController = inject[AssetsController]
  lazy val messagesController = inject[MessagesController]
  lazy val accentColorController = inject[AccentColorController]

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_collection_share, container, false)
    val listView = ViewUtils.getView(view, R.id.lv__conversation_list).asInstanceOf[ListView]
    val sendButton = ViewUtils.getView(view, R.id.cib__send_button).asInstanceOf[CursorIconButton]
    val adapter = new ShareToMultipleAdapter(getContext)
    val toolbar = ViewUtils.getView(view, R.id.t_toolbar).asInstanceOf[Toolbar]
    val messageId = getArguments.getString(ShareToMultipleFragment.MSG_ID_ARG)
    val messageData = messagesController.getMessage(MessageId(messageId)).flatMap{
      case Some(md) => Signal(md)
      case _ => Signal[MessageData]()
    }
    val assetDataSignal: SourceSignal[Option[AssetData]] = Signal[Option[AssetData]]()
    val messageTextSignal: SourceSignal[Option[String]] = Signal[Option[String]]()
    val clickSignal: SourceSignal[Unit] = Signal[Unit]()

    accentColorController.accentColor.on(Threading.Ui) {
      color => sendButton.setSolidBackgroundColor(color.getColor())
    }

    toolbar.setNavigationIcon(if (getControllerFactory.getThemeController.isDarkTheme) R.drawable.ic_action_close_light else R.drawable.ic_action_close_dark)
    toolbar.setNavigationOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = onBackPressed()
    })

    listView.setAdapter(adapter)
    getStoreFactory.getConversationStore.addConversationStoreObserverAndUpdate(adapter)

    sendButton.setVisibility(View.GONE)
    messageData.on(Threading.Ui) {
      _ => sendButton.setVisibility(View.VISIBLE)
    }

    messageData.on(Threading.Background)(md => md.msgType match {
      case Message.Type.ANY_ASSET | Message.Type.ASSET | Message.Type.VIDEO_ASSET | Message.Type.AUDIO_ASSET =>
        assetDataSignal ! None
        messageTextSignal ! None
        assetsController.assetSignal(md.assetId).on(Threading.Background)(a => assetDataSignal ! Some(a._1))
      case _ =>
        messageTextSignal ! Some(md.contentString)
        assetDataSignal ! None
    })

    val dialog = new ProgressDialog(getContext)
    dialog.setTitle(getString(R.string.conversation__action_mode__fwd__dialog__title))
    dialog.setMessage(getString(R.string.conversation__action_mode__fwd__dialog__message))
    dialog.setIndeterminate(true)
    dialog.setCancelable(true)
    dialog.setOnCancelListener(null)

    (for{
      ad <- assetDataSignal
      mt <- messageTextSignal
      _ <- clickSignal
    } yield (ad, mt)).on(Threading.Ui) {
      case (None, None) =>
        dialog.show()
      case (None, Some(text)) =>
        if (dialog.isShowing) dialog.dismiss()
        sendMessage(text, adapter.selectedConversations.toSet)
        onBackPressed()
      case (Some(assetData), _) =>
        if (dialog.isShowing) dialog.dismiss()
        (assetData.assetType, assetData.source) match {
          case (Some(AssetType.Image), Some(uri)) =>
            sendImageAsset(uri, adapter.selectedConversations.toSet)
          case (_, Some(uri)) =>
            sendAsset(uri, adapter.selectedConversations.toSet)
          case _ =>
        }
        onBackPressed()
    }

    sendButton.setOnClickListener(new OnClickListener {
      override def onClick(v: View): Unit = {
        if (adapter.selectedConversations.isEmpty) {
          return
        }
        clickSignal ! (())
      }
    })
    view
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setStyle(DialogFragment.STYLE_NO_FRAME, R.style.Theme_Dark_Preferences)
  }

  //TODO this was copied from ConversationFragment...
  private val assetErrorHandler: MessageContent.Asset.ErrorHandler = new MessageContent.Asset.ErrorHandler() {
    def noWifiAndFileIsLarge(sizeInBytes: Long, net: NetworkMode, answer: MessageContent.Asset.Answer): Unit = {
      if (getActivity == null) {
        answer.ok()
        return
      }
      val dialog: AlertDialog = ViewUtils.showAlertDialog(getActivity, R.string.asset_upload_warning__large_file__title, R.string.asset_upload_warning__large_file__message_default, R.string.asset_upload_warning__large_file__button_accept, R.string.asset_upload_warning__large_file__button_cancel, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          answer.ok()
        }
      }, new DialogInterface.OnClickListener() {
        def onClick(dialog: DialogInterface, which: Int): Unit = {
          answer.cancel()
        }
      })
      dialog.setCancelable(false)
      if (sizeInBytes > 0) {
        val fileSize: String = Formatter.formatFileSize(getContext, sizeInBytes)
        dialog.setMessage(getString(R.string.asset_upload_warning__large_file__message, fileSize))
      }
    }
  }

  private def sendAsset(assetUri: Uri, conversations: Set[IConversation]): Unit = {
    conversations.foreach{
      conversation =>
        getStoreFactory.getConversationStore.sendMessage(conversation, AssetFactory.fromContentUri(assetUri), assetErrorHandler)
    }
  }

  private def sendImageAsset(assetUri: Uri, conversations: Set[IConversation]): Unit = {
    conversations.foreach{
      conversation =>
        getStoreFactory.getConversationStore.sendMessage(conversation, ImageAssetFactory.getImageAsset(assetUri))
    }
  }

  private def sendMessage(content: String, conversations: Set[IConversation]): Unit = {
    conversations.foreach{
      conversation =>
        getStoreFactory.getConversationStore.sendMessage(conversation, content)
    }
  }

  override def onBackPressed(): Boolean = {
    getDialog.dismiss()
    true
  }
}

class ShareToMultipleAdapter(context: Context) extends ListAdapter with ConversationStoreObserver{

  var conversationsList: Option[ConversationsList] = None
  val selectedConversations = new scala.collection.mutable.HashSet[IConversation]
  private val checkBoxListener = new CompoundButton.OnCheckedChangeListener {
    override def onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean): Unit = {
      if (isChecked) {
        selectedConversations.add(buttonView.getTag.asInstanceOf[IConversation])
      } else {
        selectedConversations.remove(buttonView.getTag.asInstanceOf[IConversation])
      }
    }
  }

  override def isEnabled(position: Int): Boolean = {
    false
  }

  override def areAllItemsEnabled(): Boolean = false

  override def getItemId(position: Int): Long = position

  override def getCount: Int = conversationsList.map(_.size()).getOrElse(0)

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    var view = convertView
    if (convertView == null) {
      view = new SelectableConversationRow(context, checkBoxListener)
    }
    view.asInstanceOf[SelectableConversationRow].setConversation(getItem(position), selectedConversations.contains(getItem(position)))
    view
  }

  override def registerDataSetObserver(observer: DataSetObserver): Unit = {}

  override def getItem(position: Int): IConversation = conversationsList.get.get(position)

  override def unregisterDataSetObserver(observer: DataSetObserver): Unit = {}

  override def getViewTypeCount: Int = 1

  override def getItemViewType(position: Int): Int = 1

  override def isEmpty: Boolean = conversationsList.exists(_.size() > 0)

  override def hasStableIds: Boolean = true

  override def onConversationListUpdated(conversationsList: ConversationsList): Unit = {
    this.conversationsList = Some(conversationsList)
  }

  override def onVerificationStateChanged(conversationId: String, previousVerification: Verification, currentVerification: Verification): Unit = {}

  override def onCurrentConversationHasChanged(fromConversation: IConversation, toConversation: IConversation, conversationChangerSender: ConversationChangeRequester): Unit = {}

  override def onMenuConversationHasChanged(fromConversation: IConversation): Unit = {}

  override def onConversationListStateHasChanged(state: ConversationsListState): Unit = {}

  override def onConversationSyncingStateHasChanged(syncState: SyncState): Unit = {}
}

class SelectableConversationRow(context: Context, attrs: AttributeSet, defStyleAttr: Int, checkBoxListener: CompoundButton.OnCheckedChangeListener) extends LinearLayout(context, attrs, defStyleAttr) {
  def this(context: Context, attrs: AttributeSet, checkBoxListener: CompoundButton.OnCheckedChangeListener) = this(context, attrs, 0, checkBoxListener)
  def this(context: Context, checkBoxListener: CompoundButton.OnCheckedChangeListener) =  this(context, null, checkBoxListener)

  LayoutInflater.from(context).inflate(R.layout.row_selectable_conversation, this, true)
  val nameView = ViewUtils.getView(this, R.id.ttv__conversation_name).asInstanceOf[TypefaceTextView]
  val checkBox = ViewUtils.getView(this, R.id.rb__conversation_selected).asInstanceOf[CheckBox]
  checkBox.setOnCheckedChangeListener(checkBoxListener)

  def setConversation(conversation: IConversation, checked: Boolean): Unit = {
    nameView.setText(conversation.getName)
    checkBox.setTag(conversation)
    checkBox.setChecked(checked)
  }
}

object ShareToMultipleFragment {
  val TAG = ShareToMultipleFragment.getClass.getSimpleName

  val MSG_ID_ARG = "MSG_ID_ARG"

  def newInstance(messageId: MessageId): ShareToMultipleFragment = {
    val fragment = new ShareToMultipleFragment
    val bundle = new Bundle()
    bundle.putString(MSG_ID_ARG, messageId.str)
    fragment.setArguments(bundle)
    fragment
  }

  trait Container
}
