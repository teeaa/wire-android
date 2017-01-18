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

import java.util
import java.util.concurrent.CopyOnWriteArraySet

import com.waz.ZLog._
import com.waz.api.{IConversation, Message, TypeFilter}
import com.waz.model._
import com.waz.service.ZMessaging
import com.waz.threading.SerialDispatchQueue
import com.waz.utils.events.{EventStream, Signal, SourceSignal}
import com.waz.zclient.controllers.collections.CollectionsObserver
import com.waz.zclient.conversation.CollectionController.CollectionInfo
import com.waz.zclient.{Injectable, Injector}

trait ICollectionsController {

  val focusedItem: SourceSignal[Option[MessageData]]
  val conversationName: Signal[String]

  def openCollection(): Unit

  def closeCollection(): Unit

  def requestPreviousItem(): Unit

  def requestNextItem(): Unit

  def openShareCollectionItem(messageData: MessageData): Unit

  def closeShareCollectionItem(): Unit

  def addObserver(collectionsObserver: CollectionsObserver): Unit

  def removeObserver(collectionsObserver: CollectionsObserver): Unit
}

class CollectionController(implicit injector: Injector) extends Injectable with ICollectionsController {

  private implicit val tag: LogTag = logTagFor[CollectionController]

  private implicit val dispatcher = new SerialDispatchQueue(name = "CollectionController")

  val zms = inject[Signal[ZMessaging]]

  val currentConv = zms.flatMap(_.convsStats.selectedConversationId).collect { case Some(id) => id }

  val msgStorage = zms.map(_.messagesStorage)

  val assetStorage = zms.map(_.assetsStorage)

  private val observers: java.util.Set[CollectionsObserver] = new util.HashSet[CollectionsObserver]

  val conversation = zms.zip(currentConv) flatMap { case (zms, convId) => zms.convsStorage.signal(convId) }

  val openedCollection = Signal[Option[CollectionInfo]]()

  val openContextMenuForMessage = EventStream[MessageData]()

  val clickedMessage = EventStream[MessageData]()

  override val conversationName = conversation map (data => if (data.convType == IConversation.Type.GROUP) data.name.filter(!_.isEmpty).getOrElse(data.generatedName) else data.generatedName)

  override val focusedItem: SourceSignal[Option[MessageData]] = Signal(None)

  private def performOnObservers(func: (CollectionsObserver) => Unit) = {
    val collectionObservers: CopyOnWriteArraySet[CollectionsObserver] = new CopyOnWriteArraySet[CollectionsObserver](observers)
    import scala.collection.JavaConversions._
    for (observer <- collectionObservers) {
      func(observer)
    }
  }

  override def openCollection() = performOnObservers(_.openCollection())

  override def closeCollection() = { performOnObservers(_.closeCollection()); openedCollection ! None }

  override def requestPreviousItem(): Unit = performOnObservers(_.previousItemRequested())

  override def requestNextItem(): Unit = performOnObservers(_.nextItemRequested())

  override def openShareCollectionItem(messageData: MessageData): Unit = performOnObservers(_.shareCollectionItem(messageData))

  override def closeShareCollectionItem(): Unit = {performOnObservers(_.closeCollectionShare())}

  override def addObserver(collectionsObserver: CollectionsObserver): Unit = observers.add(collectionsObserver)

  override def removeObserver(collectionsObserver: CollectionsObserver): Unit = observers.remove(collectionsObserver)
}

class StubCollectionController extends ICollectionsController{

  override val focusedItem: SourceSignal[Option[MessageData]] = Signal(None)
  override val conversationName: Signal[String] = Signal("")

  override def openCollection(): Unit = {}

  override def closeCollection(): Unit = {}

  override def requestPreviousItem(): Unit = {}

  override def requestNextItem(): Unit = {}

  override def openShareCollectionItem(messageData: MessageData): Unit = {}

  override def closeShareCollectionItem(): Unit = {}

  override def addObserver(collectionsObserver: CollectionsObserver): Unit = {}

  override def removeObserver(collectionsObserver: CollectionsObserver): Unit = {}
}

object CollectionController {

  val GridColumns = 4

  case class CollectionInfo(conversation: ConversationData, empty: Boolean)

  def injectedCollectionController(injectable: Injectable)(implicit injector: Injector): CollectionController =  {
    injectable.inject[CollectionController]
  }

  trait ContentType {
    val msgTypes: Seq[Message.Type]
    val typeFilter: Seq[TypeFilter]
  }

  case object Links extends ContentType {
    override val msgTypes = Seq(Message.Type.RICH_MEDIA)
    override val typeFilter: Seq[TypeFilter] = Seq(TypeFilter(Message.Type.RICH_MEDIA, None))
  }

  case object Images extends ContentType {
    override val msgTypes = Seq(Message.Type.ASSET)
    override val typeFilter: Seq[TypeFilter] = Seq(TypeFilter(Message.Type.ASSET, None))
  }

  //Now we can add more types to this sequence for the "others" category
  case object Files extends ContentType {
    override val msgTypes = Seq(Message.Type.ANY_ASSET)
    override val typeFilter: Seq[TypeFilter] = Seq(TypeFilter(Message.Type.ANY_ASSET, None))
  }

  case object AllContent extends ContentType {
    //feels a little bit messy... maybe think of a neater way to represent the types
    override val msgTypes = Images.msgTypes ++ Files.msgTypes ++ Links.msgTypes
    override val typeFilter: Seq[TypeFilter] = Seq(
      TypeFilter(Message.Type.ASSET, Some(8)),
      TypeFilter(Message.Type.RICH_MEDIA, Some(3)),
      TypeFilter(Message.Type.ANY_ASSET, Some(3))
    )
  }
}
