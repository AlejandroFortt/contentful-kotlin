package com.contentful.tea.kotlin.information

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import com.contentful.tea.kotlin.BuildConfig
import com.contentful.tea.kotlin.R

data class Platform(
    val name: String,
    val hosted: String,
    val gitHub: String,
    @DrawableRes val logo: Int
)

class AboutFragment : Fragment() {
    private val platforms: List<Platform> = listOf(
        Platform(
            "Java",
            "https://the-example-app-java.contentful.com",
            "https://github.com/contentful/the-example-app.java",
            R.drawable.icon_java
        ),
        Platform(
            "JavaScript",
            "https://the-example-app-nodejs.contentful.com/",
            "https://github.com/contentful/the-example-app.nodejs",
            R.drawable.icon_nodejs
        ),
        Platform(
            ".Net",
            "https://the-example-app-csharp.contentful.com",
            "https://github.com/contentful/the-example-app.csharp",
            R.drawable.icon_dotnet
        ),
        Platform(
            "Ruby",
            "https://the-example-app-rb.contentful.com",
            "https://github.com/contentful/the-example-app.rb",
            R.drawable.icon_ruby
        ),
        Platform(
            "Php",
            "https://the-example-app-php.contentful.com",
            "https://github.com/contentful/the-example-app.php",
            R.drawable.icon_php
        ),
        Platform(
            "Python",
            "https://the-example-app-py.contentful.com",
            "https://github.com/contentful/the-example-app.py",
            R.drawable.icon_python
        ),
        Platform(
            "Swift",
            "",
            "https://github.com/contentful/the-example-app.swift",
            R.drawable.icon_swift
        ),
        Platform(
            "Android",
            "",
            "https://github.com/contentful/the-example-app.kotlin",
            R.drawable.icon_android
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_about, container, false)

        val aboutDescription = root.findViewById<TextView>(R.id.about_description)
        val aboutOthersContainer = root.findViewById<ViewGroup>(R.id.about_others)
        val aboutVersion = root.findViewById<TextView>(R.id.about_version)

        aboutDescription.apply {
            text = Html.fromHtml(getString(R.string.about_description), 0)
            movementMethod = LinkMovementMethod.getInstance()
        }

        platforms.map { platform ->
            val platformView =
                inflater.inflate(R.layout.item_about_others, aboutOthersContainer, false).apply {
                    val aboutOthersLogo = findViewById<View>(R.id.about_others_logo)
                    aboutOthersLogo.setBackgroundResource(platform.logo)
                    aboutOthersLogo.setOnClickListener {
                        openLink(platform.hosted.ifEmpty { platform.gitHub })
                    }
                }
            aboutOthersContainer.addView(platformView)
        }

        @SuppressWarnings("SetTextI18n")
        aboutVersion.text = "v${BuildConfig.VERSION_NAME}"

        return root
    }

    private fun openLink(uri: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }
}
