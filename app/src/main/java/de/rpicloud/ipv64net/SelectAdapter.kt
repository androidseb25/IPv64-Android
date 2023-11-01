package de.rpicloud.ipv64net

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import java.util.Locale

class SelectAdapter(
    private val mContext: Context,
    private val mLayoutResourceId: Int,
    strList: MutableList<String>
) :
    ArrayAdapter<String>(mContext, mLayoutResourceId, strList) {
    private val strings: MutableList<String> = ArrayList(strList)
    private var allStrings: List<String> = ArrayList(strList)

    override fun getCount(): Int {
        return strings.size
    }

    override fun getItem(position: Int): String {
        return strings[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var convertView = convertView
        if (convertView == null) {
            val inflater = (mContext as Activity).layoutInflater
            convertView = inflater.inflate(mLayoutResourceId, parent, false)
        }
        try {
            val string: String = getItem(position)
            val cityAutoCompleteView = convertView!!.findViewById<View>(R.id.spinner_tv) as TextView
            cityAutoCompleteView.text = string
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return convertView!!
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun convertResultToString(resultValue: Any): String {
                return (resultValue as String)
            }

            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                if (constraint != null) {
                    val stringSuggestion: MutableList<String> = ArrayList()
                    for (string in allStrings) {
                        if (string.lowercase(Locale.getDefault()).startsWith(
                                constraint.toString()
                                    .lowercase(Locale.getDefault())
                            )
                        ) {
                            stringSuggestion.add(string)
                        }
                    }
                    filterResults.values = stringSuggestion
                    filterResults.count = stringSuggestion.size
                }
                return filterResults
            }

            override fun publishResults(
                constraint: CharSequence?,
                results: FilterResults
            ) {
                strings.clear()
                if (results.count > 0) {
                    for (result in results.values as List<*>) {
                        if (result is String) {
                            strings.add(result)
                        }
                    }
                    notifyDataSetChanged()
                } else if (constraint == null) {
                    strings.addAll(allStrings)
                    notifyDataSetInvalidated()
                }
            }
        }
    }
}