package historyRecyclerView

class HistoryObject(private val rideId: String,private val time: String) {

    fun getRideId(): String { return rideId}
    fun getTime(): String { return time}
}