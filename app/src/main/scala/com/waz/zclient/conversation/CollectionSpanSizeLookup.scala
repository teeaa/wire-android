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
package com.waz.zclient.conversation

import android.annotation.TargetApi
import android.os.Build
import android.support.v7.widget.{GridLayoutManager, RecyclerView}
import android.util.SparseArray
import com.waz.ZLog._
import com.waz.utils.events.EventContext
import com.waz.utils.returning

class CollectionSpanSizeLookup(val spanCount: Int, val adapter: CollectionAdapter)(implicit eventContext: EventContext) extends GridLayoutManager.SpanSizeLookup {

  private implicit val tag: LogTag = logTagFor[CollectionSpanSizeLookup]

  private val spanIndexCache = new SparseArray[Int]()
  private val spanSizeCache = new SparseArray[Int]()

  adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
    override def onChanged(): Unit = clearCache()

    override def onItemRangeRemoved(positionStart: Int, itemCount: Int): Unit = clearCacheFromPosition(positionStart - 1)

    override def onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int): Unit = {
      val minPos = Math.min(fromPosition, toPosition)
      clearCacheFromPosition(minPos - 1)
    }

    override def onItemRangeChanged(positionStart: Int, itemCount: Int): Unit = clearCacheFromPosition(positionStart - 1)

    override def onItemRangeChanged(positionStart: Int, itemCount: Int, payload: scala.Any): Unit = clearCacheFromPosition(positionStart - 1)

    override def onItemRangeInserted(positionStart: Int, itemCount: Int): Unit = clearCacheFromPosition(positionStart - 1)
  })

  override def getSpanSize(position: Int): Int = {
    if (spanSizeIsCached(position)) {
      return getCachedSpanSize(position)
    }
    if (adapter.isFullSpan(position)) {
      addSpanIndexToCache(position, 0)
      returning(spanCount)(addSpanSizeToCache(position, _))
    } else if (isLastBeforeHeader(position)) {
      val columnIndex = returning(getSpanIndex(position, spanCount))(addSpanIndexToCache(position, _))
      returning(spanCount - columnIndex)(addSpanSizeToCache(position, _))
    } else {
      addSpanIndexToCache(position, getSpanIndex(position, spanCount))
      returning(1)(addSpanSizeToCache(position, _))
    }
  }

  def isLastBeforeHeader(position: Int): Boolean = {
    if (position == adapter.getItemCount - 1){
      return true
    }
    val headerId = adapter.getHeaderId(position)
    val nextPosition = position + 1
    val res = nextPosition >= 0 && nextPosition < adapter.getItemCount && !headerId.equals(adapter.getHeaderId(nextPosition))
    res
  }

  def isFirstAfterHeader(position: Int): Boolean ={
    if (position == 0) {
      return true
    }
    if (position == adapter.getItemCount - 1) {
      return false
    }
    if (isLastBeforeHeader(position - 1)) {
      return true
    }
    false
  }

  override def getSpanIndex(position: Int, spanCount: Int): Int ={
    if (spanIndexIsCached(position)) {
      return getCachedSpanIndex(position)
    }

    if (isFirstAfterHeader(position)){
      return 0
    }

    val indexBefore = getSpanIndex(position - 1, spanCount)
    (indexBefore + 1) % spanCount
  }

  override def isSpanIndexCacheEnabled: Boolean = false

  def spanIndexIsCached(position: Int): Boolean = {
    spanIndexCache.get(position, -1) != -1
  }

  def getCachedSpanIndex(position: Int): Int ={
    spanIndexCache.get(position, 0)
  }

  def addSpanIndexToCache(position: Int, index: Int): Unit ={
    spanIndexCache.put(position, index)
  }

  def spanSizeIsCached(position: Int): Boolean ={
    spanSizeCache.get(position, -1) != -1
  }

  def getCachedSpanSize(position: Int): Int={
    spanSizeCache.get(position, 0)
  }

  def addSpanSizeToCache(position: Int, size: Int): Unit = {
    spanSizeCache.put(position, size)
  }

  def clearCache(): Unit ={
    spanIndexCache.clear()
    spanSizeCache.clear()
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  def clearCacheFromPosition(position: Int): Unit = {
    if (position <= 0 || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
      clearCache()
      return
    }
    if (position <= spanIndexCache.size() - 1) {
      spanIndexCache.removeAtRange(position, spanIndexCache.size() - position)
    }
    if (position <= spanSizeCache.size() - 1) {
      spanSizeCache.removeAtRange(position, spanSizeCache.size() - position)
    }
  }
}
