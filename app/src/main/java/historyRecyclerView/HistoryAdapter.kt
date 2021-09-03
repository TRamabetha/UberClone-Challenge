package historyRecyclerView

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.trajikworks.uberclonechallenge.HistoryPageActivity
import com.trajikworks.uberclonechallenge.R

class HistoryAdapter(private val itemList: List<HistoryObject>, val context: Context) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolders>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolders {
        val layoutView = LayoutInflater.from(parent.context).inflate(R.layout.item_history,null,false)
        val lp = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)
        layoutView.layoutParams = lp

        return HistoryViewHolders(layoutView)
    }

    override fun onBindViewHolder(holder: HistoryViewHolders, position: Int) {
        holder.bindItems(itemList[position])
    }

    override fun getItemCount(): Int {
        return itemList.size
    }

    class HistoryViewHolders(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnClickListener{


        fun bindItems(historyId: HistoryObject){
            super.itemView
            itemView.setOnClickListener(this)

            itemView.findViewById<TextView>(R.id.ih_rideID).text = historyId.getRideId()
            itemView.findViewById<TextView>(R.id.ih_time).text = historyId.getTime()
        }

        override fun onClick(v: View?) {
            val intent = Intent(v!!.context, HistoryPageActivity::class.java)
            val b = Bundle()
            b.putString("rideId", itemView.findViewById<TextView>(R.id.ih_rideID).text.toString())
            intent.putExtras(b)
            v.context.startActivity(intent)
        }
    }
}