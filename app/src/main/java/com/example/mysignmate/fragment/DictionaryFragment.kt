package com.example.mysignmate.fragment

// YourFragment.kt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView

import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mysignmate.DictionaryAdapter
import com.example.mysignmate.DictionaryItem
import com.example.mysignmate.R

class DictionaryFragment : Fragment() {

    private lateinit var dictionaryAdapter: DictionaryAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_dictionary, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.list_dictionary)

        // Sample data (replace with your actual data)
        val itemList = listOf(
            DictionaryItem("A", R.drawable.a,"https://www.youtube.com/embed/mrtQKEIWYqc"),
            DictionaryItem("Awak", R.drawable.awak,"https://www.youtube.com/embed/GSERjhVh82E"),
            DictionaryItem("B", R.drawable.b, "https://www.youtube.com/embed/LdM4S6BST0g"),
            DictionaryItem("Bagaimana", R.drawable.bagaimana,"https://www.youtube.com/embed/s6jw3JLtwo0\\"),
            DictionaryItem("C", R.drawable.c,"https://www.youtube.com/embed/Fbv-7HAS5wU"),
            DictionaryItem("D", R.drawable.d,"https://www.youtube.com/embed/XKbgTKnANcY"),
            DictionaryItem("E", R.drawable.e,"https://www.youtube.com/embed/Sxv8yrdfT9Q"),
            DictionaryItem("F", R.drawable.f,"https://www.youtube.com/embed/AcSxG1__wxs"),
            DictionaryItem("G", R.drawable.g,"https://www.youtube.com/embed/l-MF20_qOWs"),
            DictionaryItem("H", R.drawable.h,"https://www.youtube.com/embed/UujVOdmfQNI"),
            DictionaryItem("I", R.drawable.i,"https://www.youtube.com/embed/xGonomZ36bw"),
            DictionaryItem("J", R.drawable.j,"https://www.youtube.com/embed/HeGhbHKOK9k"),
            DictionaryItem("K", R.drawable.k,"https://www.youtube.com/embed/r6Ywn32kki8"),
            DictionaryItem("Ke", R.drawable.ke,"https://www.youtube.com/embed/ZwtkTkmx5ME"),
            DictionaryItem("L", R.drawable.l,"https://www.youtube.com/embed/Xn_VXjLL6Cc"),
            DictionaryItem("M", R.drawable.m,"https://www.youtube.com/embed/-udLp7Vs_D8"),
            DictionaryItem("N", R.drawable.n,"https://www.youtube.com/embed/2WugHgFRE9k"),
            DictionaryItem("O", R.drawable.o,"https://www.youtube.com/embed/8SrQFU-OSC0"),
            DictionaryItem("P", R.drawable.p,"https://www.youtube.com/embed/Zry4ncgRz7s"),
            DictionaryItem("Perpustakaan", R.drawable.perpustakaan,"https://www.youtube.com/embed/yG12wX1g4Fk"),
            DictionaryItem("Q", R.drawable.q,"https://www.youtube.com/embed/4UDPiFs3nys"),
            DictionaryItem("R", R.drawable.r,"https://www.youtube.com/embed/HFNc88dL6wQ"),
            DictionaryItem("S", R.drawable.s,"https://www.youtube.com/embed/jc58PaoNuiA"),
            DictionaryItem("T", R.drawable.t,"https://www.youtube.com/embed/qoB09rjAo88"),
            DictionaryItem("V", R.drawable.u,"https://www.youtube.com/embed/n861kdyURPQ"),
            DictionaryItem("V", R.drawable.v,"https://www.youtube.com/embed/aQ94CfBI-oc"),
            DictionaryItem("W", R.drawable.w,"https://www.youtube.com/embed/-P2m7bKLOgg"),
            DictionaryItem("X", R.drawable.x,"https://www.youtube.com/embed/S_EV2-nJYPY"),
            DictionaryItem("Y", R.drawable.y,"https://www.youtube.com/embed/0Ii73lt0pmk"),
            DictionaryItem("Z", R.drawable.z,"https://www.youtube.com/embed/pnS4sNqK0yE")

            // Add more items as needed
        )


        // Create an instance of your Adapter class and attach it to the RecyclerView
        dictionaryAdapter = DictionaryAdapter(itemList)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = dictionaryAdapter

        // Assuming you have a SearchView in your layout with ID search_view
        val searchView: SearchView = view.findViewById(R.id.search_view)

        // Set up SearchView listener
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                dictionaryAdapter.filter(newText.orEmpty())
                return true
            }
        })

        return view
    }

}
