package com.example.mysignmate.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.DialogFragment
import com.example.mysignmate.R

class VideoFragment: DialogFragment() {

    companion object{
        private const val VIDEO_URL_ARG = "video_url"

        fun newInstance(videoUrl: String): VideoFragment{
            val fragment = VideoFragment()
            val args = Bundle()
            args.putString(VIDEO_URL_ARG,videoUrl)
            fragment.arguments = args

            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_video, container, false)

        val webView: WebView = view.findViewById(R.id.videoView)
        val videoUrl = arguments?.getString(VIDEO_URL_ARG) ?: ""

        val webViewSettings = webView.settings
        webViewSettings.javaScriptEnabled = true

        val html = "<iframe width=\"100%\" height=\"100%\" src=\"$videoUrl?autoplay=1\" frameborder=\"0\" allowfullscreen></iframe>"
        webView.loadData(html, "text/html", "utf-8")

        return view
    }
}