package com.michelvilleneuve.m4ts

import android.os.Parcel
import android.os.Parcelable
import android.view.View

internal class SavedState : View.BaseSavedState {
    var lines: MutableList<MyCustomDrawingView.Line> = mutableListOf()
    var texts: MutableList<MyCustomDrawingView.Text> = mutableListOf()
    var circles: MutableList<MyCustomDrawingView.Circle> = mutableListOf()
    var arcs: MutableList<MyCustomDrawingView.Arc> = mutableListOf()
    var rectangles: MutableList<MyCustomDrawingView.Rectangle> = mutableListOf()

    constructor(superState: Parcelable?) : super(superState)

    private constructor(parcel: Parcel) : super(parcel) {
        lines = parcel.createTypedArrayList(MyCustomDrawingView.Line.CREATOR)!!
        texts = parcel.createTypedArrayList(MyCustomDrawingView.Text.CREATOR)!!
        circles = parcel.createTypedArrayList(MyCustomDrawingView.Circle.CREATOR)!!
        arcs = parcel.createTypedArrayList(MyCustomDrawingView.Arc.CREATOR)!!
        rectangles = parcel.createTypedArrayList(MyCustomDrawingView.Rectangle.CREATOR)!!
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        super.writeToParcel(parcel, flags)
        parcel.writeTypedList(lines)
        parcel.writeTypedList(texts)
        parcel.writeTypedList(circles)
        parcel.writeTypedList(arcs)
        parcel.writeTypedList(rectangles)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
            override fun createFromParcel(parcel: Parcel): SavedState {
                return SavedState(parcel)
            }

            override fun newArray(size: Int): Array<SavedState?> {
                return arrayOfNulls(size)
            }
        }
    }
}
