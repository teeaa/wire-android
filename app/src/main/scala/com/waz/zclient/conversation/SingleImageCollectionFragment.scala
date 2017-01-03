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

import android.os.Bundle
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View.{OnLayoutChangeListener, OnLongClickListener, OnTouchListener}
import android.view._
import com.waz.model.AssetId
import com.waz.service.ZMessaging
import com.waz.service.messages.MessageAndLikes
import com.waz.utils.events.Signal
import com.waz.zclient.messages.controllers.MessageActionsController
import com.waz.zclient.pages.BaseFragment
import com.waz.zclient.utils.ViewUtils
import com.waz.zclient.views.ImageAssetDrawable
import com.waz.zclient.views.ImageController.WireImage
import com.waz.zclient.views.images.TouchImageView
import com.waz.zclient.{FragmentHelper, OnBackPressedListener, R}

class SingleImageCollectionFragment extends BaseFragment[CollectionFragment.Container] with FragmentHelper with OnBackPressedListener {

  lazy val collectionController = getControllerFactory.getCollectionsController

  lazy val zms = inject[Signal[ZMessaging]]
  lazy val messageActions = inject[MessageActionsController]

  lazy val messageAndLikes = zms.zip(collectionController.focusedItem).flatMap{
    case (z, Some(md)) => Signal.future(z.msgAndLikes.combineWithLikes(md))
    case _ => Signal[MessageAndLikes]()
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    val view = inflater.inflate(R.layout.fragment_single_image_collections, container, false)
    val imageView: TouchImageView = ViewUtils.getView(view, R.id.tiv__image_view)
    messageAndLikes.disableAutowiring()
    imageView.setOnLongClickListener(new OnLongClickListener {
      override def onLongClick(v: View): Boolean = {
        getView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        messageAndLikes.currentValue.exists(messageActions.showDialog)
        true
      }
    })
    val gestureDetector = new GestureDetector(getContext, new SimpleOnGestureListener(){
      override def onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = {
        if (imageView.isZoomed) return true
        if (velocityX > SingleImageCollectionFragment.MIN_FLING_THRESHOLD) {
          collectionController.requestPreviousItem()
        } else if (velocityX < -SingleImageCollectionFragment.MIN_FLING_THRESHOLD) {
          collectionController.requestNextItem()
        }
        true
      }
    })

    imageView.setOnTouchListener(new OnTouchListener {
      override def onTouch(v: View, event: MotionEvent): Boolean = gestureDetector.onTouchEvent(event)
    })

    imageView.setImageBitmap(null)
    view
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle): Unit = {
    super.onViewCreated(view, savedInstanceState)

    val imageView: TouchImageView = ViewUtils.getView(view, R.id.tiv__image_view)
    lazy val onLayoutChangeListener: OnLayoutChangeListener = new OnLayoutChangeListener {
      override def onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int): Unit = {
        if (v.getWidth > 0) {
          val assetId = AssetId(getArguments.getString(SingleImageCollectionFragment.ARG_ASSET_ID))
          setAsset(assetId)
          imageView.removeOnLayoutChangeListener(onLayoutChangeListener)
        }
      }
    }
    imageView.addOnLayoutChangeListener(onLayoutChangeListener)
  }

  override def onBackPressed(): Boolean = true

  def setAsset(assetId: AssetId): Unit = {
    val imageView: TouchImageView = ViewUtils.getView(getView, R.id.tiv__image_view)
    imageView.setImageDrawable(new ImageAssetDrawable(Signal(WireImage(assetId)), scaleType = ImageAssetDrawable.ScaleType.CenterInside))
  }
}

object SingleImageCollectionFragment {

  val TAG = SingleImageCollectionFragment.getClass.getSimpleName

  val ARG_ASSET_ID = "ARG_ASSET_ID"

  private val MIN_FLING_THRESHOLD = 1000

  def newInstance(assetId: AssetId): SingleImageCollectionFragment = {
    val fragment = new SingleImageCollectionFragment
    val bundle = new Bundle()
    bundle.putString(ARG_ASSET_ID, assetId.str)
    fragment.setArguments(bundle)
    fragment
  }
}
