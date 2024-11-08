package com.contentful.tea.kotlin.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.contentful.tea.kotlin.R
import com.contentful.tea.kotlin.Reloadable
import com.contentful.tea.kotlin.content.Course
import com.contentful.tea.kotlin.dependencies.Dependencies
import com.contentful.tea.kotlin.dependencies.DependenciesProvider
import com.contentful.tea.kotlin.extensions.isNetworkError
import com.contentful.tea.kotlin.extensions.showError
import com.contentful.tea.kotlin.extensions.showNetworkError
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CourseOverviewFragment : Fragment(), Reloadable {
    private var courseSlug: String? = null
    private var firstLessonSlug: String? = null

    private lateinit var dependencies: Dependencies

    private lateinit var overview_next: FloatingActionButton
    private lateinit var overview_container: LinearLayout
    private lateinit var overview_title: TextView
    private lateinit var overview_description: TextView
    private lateinit var overview_duration: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments
        if (args != null) {
            courseSlug = CourseOverviewFragmentArgs.fromBundle(args).courseSlug
        }

        if (activity !is DependenciesProvider) {
            throw IllegalStateException("Activity must implement Dependency provider.")
        }

        dependencies = (activity as DependenciesProvider).dependencies()
    }

    override fun onResume() {
        super.onResume()

        activity?.findViewById<Toolbar>(R.id.main_toolbar)?.findViewById<View>(R.id.logo_image)
            ?.setOnClickListener { goHome() }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_course_overview, container, false)
        overview_next = view.findViewById(R.id.overview_next)
        overview_container = view.findViewById(R.id.overview_container)
        overview_title = view.findViewById(R.id.overview_title)
        overview_description = view.findViewById(R.id.overview_description)
        overview_duration = view.findViewById(R.id.overview_duration)
        return view.rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        overview_next.setOnClickListener { onNextButtonClicked() }

        updateViews()
        super.onViewCreated(view, savedInstanceState)
    }

    override fun reload() {
        overview_container.removeAllViews()
        updateViews()
    }

    private fun updateViews() {
        courseSlug?.let {
            dependencies
                .contentInfrastructure
                .fetchCourseBySlug(
                    courseSlug!!,
                    errorCallback = ::errorFetchingCourseBySlug
                ) { course ->
                    activity?.runOnUiThread {
                        updateData(course)
                    }
                }
        }
    }

    private fun updateData(course: Course) {
        if (overview_title == null) {
            return
        }

        firstLessonSlug = if (course.lessons.isNotEmpty()) course.lessons.first().slug else null
        val parser = dependencies.markdown

        overview_title.text = parser.parse(course.title)
        overview_description.text = parser.parse(course.description)
        overview_duration.text = parser.parse(
            getString(
                R.string.lesson_duration,
                course.duration,
                course.skillLevel
            )
        )

        val inflater = LayoutInflater.from(context)

        course.lessons.forEach { lesson ->
            val index = course.lessons.indexOf(lesson)
            val view = inflater.inflate(R.layout.item_lesson, overview_container, false)

            val titleTextView: TextView = view.findViewById(R.id.lesson_item_title)
            val descriptionTextView: TextView = view.findViewById(R.id.lesson_item_description)

            titleTextView.text = parser.parse(lesson.title)
            descriptionTextView.text = parser.parse(
                getString(R.string.lesson_number, index + 1)
            )

            view.setOnClickListener {
                lessonClicked(lesson.slug)
            }

            overview_container.addView(view)
        }
    }

    private fun lessonClicked(lessonSlug: String) {
        val navController = NavHostFragment.findNavController(this)
        val action = CourseOverviewFragmentDirections.openLesson(courseSlug!!, lessonSlug)
        navController.navigate(action)
    }

    private fun onNextButtonClicked() = firstLessonSlug?.let { lessonClicked(it) }

    private fun errorFetchingCourseBySlug(throwable: Throwable) {
        activity?.apply {
            if (throwable.isNetworkError()) {
                showNetworkError()
            } else {
                val navController = NavHostFragment.findNavController(this@CourseOverviewFragment)
                showError(
                    message = getString(R.string.error_fetching_course_from_slug, courseSlug),
                    moreTitle = getString(R.string.error_open_settings_button),
                    error = throwable,
                    moreHandler = {
                        val action = CourseOverviewFragmentDirections.openSettings()
                        navController.navigate(action)
                    },
                    okHandler = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun goHome() {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        val navController = NavHostFragment.findNavController(this)
        val action = CourseOverviewFragmentDirections.openHome()
        navController.navigate(action, navOptions)
    }
}
