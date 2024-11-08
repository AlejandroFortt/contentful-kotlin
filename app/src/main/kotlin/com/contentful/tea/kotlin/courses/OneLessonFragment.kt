package com.contentful.tea.kotlin.courses

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.contentful.tea.kotlin.R
import com.contentful.tea.kotlin.Reloadable
import com.contentful.tea.kotlin.content.Course
import com.contentful.tea.kotlin.content.LessonModule
import com.contentful.tea.kotlin.dependencies.Dependencies
import com.contentful.tea.kotlin.dependencies.DependenciesProvider
import com.contentful.tea.kotlin.extensions.isNetworkError
import com.contentful.tea.kotlin.extensions.saveToClipboard
import com.contentful.tea.kotlin.extensions.setImageResourceFromUrl
import com.contentful.tea.kotlin.extensions.showError
import com.contentful.tea.kotlin.extensions.showNetworkError
import com.contentful.tea.kotlin.extensions.toast
import com.google.android.material.floatingactionbutton.FloatingActionButton

class OneLessonFragment : Fragment(), Reloadable {
    private var courseSlug: String? = null
    private var lessonSlug: String? = null

    private lateinit var dependencies: Dependencies

    private lateinit var lesson_module_container: LinearLayout
    private lateinit var lesson_next_button: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val args = arguments
        if (args != null) {
            courseSlug = OneLessonFragmentArgs.fromBundle(args).courseSlug
            lessonSlug = OneLessonFragmentArgs.fromBundle(args).lessonSlug
        }

        if (activity !is DependenciesProvider) {
            throw IllegalStateException("Activity must implement Dependency provider.")
        }

        dependencies = (activity as DependenciesProvider).dependencies()

        val view = inflater.inflate(R.layout.fragment_lesson, container, false)
        lesson_module_container = view.findViewById(R.id.lesson_module_container)
        lesson_next_button = view.findViewById(R.id.lesson_next_button)

        return view
    }

    override fun onResume() {
        super.onResume()

        activity?.findViewById<Toolbar>(R.id.main_toolbar)?.findViewById<View>(R.id.logo_image)
            ?.setOnClickListener { goToCourse() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadViews()
    }

    override fun reload() {
        lesson_module_container.removeAllViews()
        loadViews()
    }

    private fun loadViews() {
        courseSlug?.apply {
            dependencies
                .contentInfrastructure
                .fetchCourseBySlug(
                    this,
                    ::lessonNotFound
                ) { course ->
                    activity?.runOnUiThread {
                        updateData(course)
                    }
                }
        }
    }

    private fun updateData(course: Course) {
        if (lesson_next_button == null) {
            return
        }

        val selectedLesson = course.lessons.firstOrNull { it.slug == lessonSlug }
        if (selectedLesson == null) {
            lessonNotFound(
                IllegalStateException("""Lesson "$lessonSlug" in "$courseSlug" not found.""")
            )
        } else {
            val currentIndex = course.lessons.indexOf(selectedLesson)
            if (currentIndex == course.lessons.lastIndex) {
                lesson_next_button?.hide()
            } else {
                lesson_next_button?.setOnClickListener {
                    nextLessonClicked(course.lessons[currentIndex + 1].slug)
                }
            }

            selectedLesson.modules.forEach {
                addModule(it)
            }
        }
    }

    private fun addModule(
        module: LessonModule,
        inflater: LayoutInflater = LayoutInflater.from(context)
    ) = when (module) {
        is LessonModule.CodeSnippet -> {
            lesson_module_container.addView(createCodeView(inflater, module))
        }
        is LessonModule.Image -> {
            lesson_module_container.addView(createImageView(inflater, module))
        }
        is LessonModule.Copy -> {
            lesson_module_container.addView(createCopyView(inflater, module))
        }
    }

    private fun createCodeView(inflater: LayoutInflater, module: LessonModule.CodeSnippet): View {
        val codeView = inflater.inflate(R.layout.lesson_module_code, lesson_module_container, false)

        val languageSelector = codeView.findViewById<Spinner>(R.id.module_code_language_selector)
        val codeSource = codeView.findViewById<TextView>(R.id.module_code_source)

        val languageAdapter = ArrayAdapter(
            requireActivity(),
            R.layout.item_language_spinner,
            R.id.language_item_name,
            resources.getStringArray(R.array.code_languages)
        )
        languageAdapter.setDropDownViewResource(R.layout.item_language_spinner)

        languageSelector.adapter = languageAdapter
        languageSelector.setSelection(0)

        languageSelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(adapterView: AdapterView<*>?) {
                codeSource.text = getString(R.string.module_code_select_language)
            }

            override fun onItemSelected(
                adapterView: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val language = resources.getStringArray(R.array.code_languages)[position]
                codeSource.text = sourceCodeFromLanguageIndex(module, language)
            }
        }

        codeSource.text = module.javaAndroid
        codeSource.setOnClickListener {
            requireActivity().saveToClipboard(
                languageSelector.selectedItem.toString(),
                codeSource.text.toString()
            )
            requireActivity().toast(getString(R.string.module_code_source_copied))
        }

        return codeView
    }

    private fun createImageView(inflater: LayoutInflater, module: LessonModule.Image): View {
        val view = inflater.inflate(R.layout.lesson_module_image, lesson_module_container, false)

        val imageCaption = view.findViewById<TextView>(R.id.module_image_caption)
        val imageView = view.findViewById<ImageView>(R.id.module_image_image)

        imageCaption.text = dependencies.markdown.parse(module.caption)
        imageCaption.movementMethod = LinkMovementMethod.getInstance()

        imageView.setImageResourceFromUrl(
            module.image,
            R.mipmap.ic_launcher_foreground
        )

        return view
    }

    private fun createCopyView(inflater: LayoutInflater, module: LessonModule.Copy): View {
        val view = inflater.inflate(R.layout.lesson_module_copy, lesson_module_container, false)

        val copyText = view.findViewById<TextView>(R.id.module_copy_text)

        copyText.text = dependencies.markdown.parse(module.copy)
        copyText.movementMethod = LinkMovementMethod.getInstance()

        return view
    }

    private fun sourceCodeFromLanguageIndex(
        codeModule: LessonModule.CodeSnippet,
        language: String
    ): CharSequence = when (language.lowercase()) {
        "curl" -> codeModule.curl
        "dotnet" -> codeModule.dotNet
        "javascript" -> codeModule.javascript
        "java" -> codeModule.java
        "javaandroid" -> codeModule.javaAndroid
        "php" -> codeModule.php
        "python" -> codeModule.python
        "ruby" -> codeModule.ruby
        "swift" -> codeModule.swift
        else -> codeModule.javaAndroid
    }

    private fun nextLessonClicked(lessonSlug: String) {
        val navController = NavHostFragment.findNavController(this)
        val action = CourseOverviewFragmentDirections.openLesson(courseSlug!!, lessonSlug)
        navController.navigate(action)
    }

    private fun goToCourse() {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        val navController = NavHostFragment.findNavController(this)
        val action = CourseOverviewFragmentDirections.openCourseOverview(courseSlug!!)
        navController.navigate(action, navOptions)
    }

    private fun lessonNotFound(throwable: Throwable) {
        activity?.apply {
            if (throwable.isNetworkError()) {
                showNetworkError()
            } else {
                val navController = NavHostFragment.findNavController(this@OneLessonFragment)
                showError(
                    message = getString(R.string.error_lesson_id_not_found, courseSlug, lessonSlug),
                    moreTitle = getString(R.string.error_open_settings_button),
                    error = throwable,
                    moreHandler = {
                        val action = OneLessonFragmentDirections.openSettings()
                        navController.navigate(action)
                    },
                    okHandler = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
