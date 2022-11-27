package com.polware.weatherv2.models

import android.os.Parcel
import android.os.Parcelable

data class WeatherResponse (
    val weather: List<Weather>?,
    val main: Main?,
    val visibility: Int,
    val wind: Wind?,
    val sys: Sys?,
    val id: Int,
    val name: String?
    ): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.createTypedArrayList(Weather),
        parcel.readParcelable(Main::class.java.classLoader),
        parcel.readInt(),
        parcel.readParcelable(Wind::class.java.classLoader),
        parcel.readParcelable(Sys::class.java.classLoader),
        parcel.readInt(),
        parcel.readString()
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeTypedList(weather)
        parcel.writeParcelable(main, flags)
        parcel.writeInt(visibility)
        parcel.writeParcelable(wind, flags)
        parcel.writeParcelable(sys, flags)
        parcel.writeInt(id)
        parcel.writeString(name)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<WeatherResponse> {
        override fun createFromParcel(parcel: Parcel): WeatherResponse {
            return WeatherResponse(parcel)
        }

        override fun newArray(size: Int): Array<WeatherResponse?> {
            return arrayOfNulls(size)
        }
    }
}