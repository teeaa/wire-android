/**
 * Wire
 * Copyright (C) 2017 Wire Swiss GmbH
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
package com.waz.zclient.tracking

import com.waz.api.Message
import com.waz.model.ConversationData._
import com.waz.zclient.core.controllers.tracking.attributes.Attribute._
import com.waz.zclient.core.controllers.tracking.attributes.RangedAttribute._
import com.waz.zclient.core.controllers.tracking.attributes.{Attribute, RangedAttribute}
import com.waz.zclient.pages.main.conversation.views.MessageBottomSheetDialog.MessageAction
import com.waz.zclient.utils.AssetUtils

sealed abstract class Event(val name: String) {
  val attributes       = Map.empty[Attribute, String]
  val rangedAttributes = Map.empty[RangedAttribute, Int]
}

case class OpenedMessageActionEvent(action: MessageAction, messageType: String) extends Event("conversation.opened_message_action") {
  import MessageAction._
  override val attributes = Map(
    ACTION -> (action match {
      case DELETE_GLOBAL => "delete_for_everyone"
      case DELETE_LOCAL  => "delete_for_me"
      case COPY          => "copy"
      case EDIT          => "edit"
      case FORWARD       => "forward"
      case _             => "other"
    }),
    TYPE -> messageType
  )
}

case class CopiedMessageEvent(messageType: String) extends Event("conversation.copied_message") { override val attributes = Map(TYPE -> messageType) }
case class ForwardedMessageEvent(messageType: String) extends Event("conversation.forwarded_message") { override val attributes = Map(TYPE -> messageType) }

case class ReactedToMessageEvent(liked: Boolean,
                                 withOtto: Boolean,
                                 fromMe: Boolean,
                                 msgType: Message.Type,
                                 convType: ConversationType,
                                 lastMessage: Boolean,
                                 method: String) extends Event("conversation.reacted_to_message") {
  override val attributes = Map (
    ACTION            -> (if (liked) "like" else "unlike"),
    TYPE              -> msgType.name,
    METHOD            -> method,
    USER              -> (if (!fromMe) "receiver" else "sender"),
    IS_LAST_MESSAGE   -> String.valueOf(lastMessage),
    CONVERSATION_TYPE -> convType.name,
    WITH_BOT          -> String.valueOf(withOtto)
  )
}

case class DeletedMessageEvent(messageType: String, forEveryone: Boolean) extends Event("conversation.deleted_message") {
  override val attributes = Map(
    TYPE -> messageType,
    METHOD -> (if (forEveryone) "for_everyone" else "for_me")
  )
}

abstract class FileEvent(name: String, fileMimeType: String, fileSizeInBytes: Int) extends Event(name) {
  override val attributes = Map(
    TYPE            -> AssetUtils.assetMimeTypeToExtension(fileMimeType),
    FILE_SIZE_BYTES -> String.valueOf(fileSizeInBytes)
  )
  override val rangedAttributes = Map(FILE_SIZE_MB -> AssetUtils.assetSizeToMB(fileSizeInBytes))
}

case class SavedFileEvent(fileMimeType: String, fileSizeInBytes: Int) extends FileEvent("file.saved_file", fileMimeType, fileSizeInBytes)
case class OpenedFileEvent(fileMimeType: String, fileSizeInBytes: Int) extends FileEvent("file.opened_file", fileMimeType, fileSizeInBytes)


abstract class MessageEvent(name: String, playedByReceiver: Boolean, withOtto: Boolean, convType: ConversationType) extends Event(name) {
  override val attributes = Map(
    Attribute.USER              -> (if (playedByReceiver) "receiver" else "sender"),
    Attribute.WITH_BOT          -> String.valueOf(withOtto),
    Attribute.CONVERSATION_TYPE -> convType.name
  )
}

abstract class DurationMessageEvent(name: String, durationSec: Int, playedByReceiver: Boolean, withOtto: Boolean, convType: ConversationType)
  extends MessageEvent(name, playedByReceiver, withOtto, convType) {
  override val rangedAttributes = Map(VIDEO_AND_AUDIO_MESSAGE_DURATION -> durationSec)
}

case class PlayedVideoMessageEvent(duration: Int, playedByReceiver: Boolean, isOtto: Boolean, convType: ConversationType)
  extends DurationMessageEvent("media.played_video_message", duration, playedByReceiver, isOtto, convType)

case class PlayedAudioMessageEvent(fileMimeType: String, durationSec: Int, playedByReceiver: Boolean, isOtto: Boolean, convType: ConversationType) extends Event("media.played_audio_message") {
  override val attributes = Map(
    TYPE              -> AssetUtils.assetMimeTypeToExtension(fileMimeType),
    USER              -> (if (playedByReceiver) "receiver" else "sender"),
    WITH_BOT          -> String.valueOf(isOtto),
    CONVERSATION_TYPE -> convType.name
  )
}

case class PlayedYouTubeMessageEvent(playedByReceiver: Boolean, isOtto: Boolean, convType: ConversationType)
  extends MessageEvent("media.played_youtube_message", playedByReceiver, isOtto, convType)

abstract class CollectionsEvent(name: String, conversationType: ConversationType, withBot: Boolean) extends Event("collections." + name){
  protected val baseAttributes = Map(
    WITH_BOT          -> String.valueOf(withBot),
    CONVERSATION_TYPE -> conversationType.name
  )
}
case class OpenedCollectionsEvent(isEmpty: Boolean, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("opened_collections", conversationType, withBot){
  override val attributes = baseAttributes ++ Map(
    IS_EMPTY -> String.valueOf(isEmpty)
  )
}
case class OpenedItemCollectionsEvent(messageType: String, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("opened_item", conversationType, withBot){
  override val attributes = baseAttributes ++ Map(
    TYPE -> messageType
  )
}
case class OpenedItemMenuCollectionsEvent(messageType: String, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("opened_item_menu", conversationType, withBot){
  override val attributes = baseAttributes ++ Map(
    TYPE -> messageType
  )
}
case class DidItemActionCollectionsEvent(messageAction: MessageAction, messageType: String, conversationType: ConversationType, withBot: Boolean) extends CollectionsEvent("did_item_action", conversationType, withBot){
  import MessageAction._
  override val attributes = baseAttributes ++ Map(
    TYPE   -> messageType,
    ACTION -> (messageAction match {
      case DELETE_GLOBAL => "delete_for_everyone"
      case DELETE_LOCAL  => "delete_for_me"
      case COPY          => "copy"
      case EDIT          => "edit"
      case FORWARD       => "forward"
      case _             => "other"
    })
  )
}
