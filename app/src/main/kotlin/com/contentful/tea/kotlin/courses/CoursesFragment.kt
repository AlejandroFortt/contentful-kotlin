package com.contentful.tea.kotlin.courses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.contentful.tea.kotlin.R
import com.contentful.tea.kotlin.Reloadable
import com.contentful.tea.kotlin.content.Category
import com.contentful.tea.kotlin.content.Course
import com.contentful.tea.kotlin.dependencies.Dependencies
import com.contentful.tea.kotlin.dependencies.DependenciesProvider
import com.contentful.tea.kotlin.extensions.isNetworkError
import com.contentful.tea.kotlin.extensions.showError
import com.contentful.tea.kotlin.extensions.showNetworkError
import com.contentful.tea.kotlin.home.HomeFragmentDirections
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout

class CoursesFragment : Fragment(), Reloadable {
    private var categorySlug: String = ""

    private lateinit var dependencies: Dependencies

    private lateinit var coursesContainer: LinearLayout
    private lateinit var coursesTopNavigation: TabLayout
    private lateinit var coursesBottomNavigation: BottomNavigationView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val args = arguments
        if (args != null) {
            categorySlug = CoursesFragmentArgs.fromBundle(args).categorySlug
        }

        if (activity !is DependenciesProvider) {
            throw IllegalStateException("Activity must implement Dependency provider.")
        }

        dependencies = (activity as DependenciesProvider).dependencies()

        val view = inflater.inflate(R.layout.fragment_courses, container, false)
        coursesContainer = view.findViewById(R.id.courses_container)
        coursesTopNavigation = view.findViewById(R.id.courses_top_navigation)
        coursesBottomNavigation = view.findViewById(R.id.courses_bottom_navigation)

        return view
    }

    override fun onResume() {
        super.onResume()

        activity?.findViewById<Toolbar>(R.id.main_toolbar)?.findViewById<View>(R.id.logo_image)
            ?.setOnClickListener { goHome() }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCurses()
    }

    override fun reload() {
        coursesContainer.removeAllViews()
        coursesTopNavigation.removeAllTabs()
        loadCurses()
    }

    private fun loadCurses() {
        dependencies
            .contentInfrastructure
            .fetchAllCategories(errorCallback = ::errorFetchingAllCategories) { categories ->
                activity?.runOnUiThread {
                    updateCategories(categories)
                    if (!isAllCategory()) {
                        updateSingleCategory(categories)
                    }
                }
            }

        if (isAllCategory()) {
            dependencies
                .contentInfrastructure
                .fetchAllCourses(errorCallback = ::errorFetchingAllCourses) { courses ->
                    activity?.runOnUiThread {
                        updateCourses(courses)
                    }
                }
        }

        coursesBottomNavigation.setOnNavigationItemSelectedListener {
            if (activity != null) {
                bottomNavigationItemSelected(it)
                true
            } else {
                false
            }
        }
    }

    private fun updateSingleCategory(categories: List<Category>) {
        if (coursesBottomNavigation == null) {
            return
        }

        val category = categories.find { it.slug == categorySlug }
        if (category != null) {
            fetchFromCategory(category)
        } else {
            errorCategoryNotFound()
        }
    }

    private fun fetchFromCategory(category: Category) {
        dependencies
            .contentInfrastructure
            .fetchAllCoursesOfCategoryId(
                category.id,
                errorCallback = { throwable ->
                    errorFetchingAllCoursesFromOneCategory(category, throwable)
                }
            ) { courses ->
                activity?.runOnUiThread {
                    updateCourses(courses)
                }
            }
    }

    private fun isAllCategory() = categorySlug.isEmpty() || categorySlug == "all"

    private fun updateCourses(courses: List<Course>) {
        if (coursesContainer == null) {
            return
        }

        val navController =
            Navigation.findNavController(requireActivity(), R.id.navigation_host_fragment)

        val parser = dependencies.markdown

        courses.forEach { course ->
            val courseView = layoutInflater.inflate(R.layout.course_card, coursesContainer, false)
            val titleTextView = courseView.findViewById<TextView>(R.id.card_title)
            val descriptionTextView = courseView.findViewById<TextView>(R.id.card_description)
            val backgroundView = courseView.findViewById<View>(R.id.card_background)
            val scrimView = courseView.findViewById<View>(R.id.card_scrim)
            val callToActionView = courseView.findViewById<View>(R.id.card_call_to_action)
            titleTextView.text = parser.parse(course.title)
            descriptionTextView.text = parser.parse(course.shortDescription)

            backgroundView.setBackgroundColor(
                resources.getColor(R.color.defaultScrim, null)
            )

            val colors = resources.getIntArray(R.array.rainbow)
            val color = colors[coursesContainer.childCount % colors.size]
            scrimView.setBackgroundColor(color)
            val l: (View) -> Unit = {
                val action = HomeFragmentDirections.openCourseOverview(course.slug)
                navController.navigate(action)
            }
            courseView.setOnClickListener(l)
            callToActionView.setOnClickListener(l)
            coursesContainer?.addView(courseView)
        }
    }

    private fun updateCategories(categories: List<Category>) {
        if (coursesTopNavigation == null) {
            return
        }

        coursesTopNavigation.addTab(
            coursesTopNavigation
                .newTab()
                .setText(R.string.categories_all)
                .setTag("")

        )

        categories.forEach { category ->
            coursesTopNavigation.addTab(
                coursesTopNavigation
                    .newTab()
                    .setText(category.title)
                    .setTag(category.slug)
            )
        }

        for (i: Int in 0 until coursesTopNavigation.tabCount) {
            val tab = coursesTopNavigation.getTabAt(i)!!
            if (tab.tag == categorySlug) {
                tab.select()
            }
        }

        if (coursesTopNavigation.selectedTabPosition == -1) {
            coursesTopNavigation.getTabAt(0)!!.select()
        }

        coursesTopNavigation.addOnTabSelectedListener(UsableOnTabListener { tab ->
            topNavigationItemSelected(tab)
        })
    }

    private fun topNavigationItemSelected(tab: TabLayout.Tab) = if (!isDetached) {
        val navController =
            Navigation.findNavController(requireActivity(), R.id.navigation_host_fragment)
        navController.navigate(CoursesFragmentDirections.openCategory(tab.tag as String))
    } else {
    }

    private fun bottomNavigationItemSelected(item: MenuItem): Boolean {
        val navController =
            Navigation.findNavController(requireActivity(), R.id.navigation_host_fragment)
        return when (item.itemId) {
            R.id.bottom_navigation_home -> {
                navigateIfNotAlreadyThere(navController, R.id.home)
                true
            }
            R.id.bottom_navigation_courses -> {
                navigateIfNotAlreadyThere(navController, R.id.courses)
                true
            }
            else -> false
        }
    }

    private fun navigateIfNotAlreadyThere(navController: NavController, @IdRes id: Int): Boolean =
        if (navController.currentDestination?.id != id) {
            navController.navigate(id)
            true
        } else {
            false
        }

    private fun errorFetchingAllCategories(throwable: Throwable) {
        activity?.apply {
            if (throwable.isNetworkError()) {
                showNetworkError()
            } else {
                val navController = NavHostFragment.findNavController(this@CoursesFragment)
                showError(
                    message = getString(R.string.error_no_categories_found),
                    moreTitle = getString(R.string.error_open_settings_button),
                    error = throwable,
                    moreHandler = {
                        val action = CoursesFragmentDirections.openSettings()
                        navController.navigate(action)
                    },
                    okHandler = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun errorFetchingAllCourses(throwable: Throwable) {
        activity?.apply {
            if (throwable.isNetworkError()) {
                showNetworkError()
            } else {
                val navController = NavHostFragment.findNavController(this@CoursesFragment)
                showError(
                    message = getString(R.string.error_fetching_all_courses),
                    moreTitle = getString(R.string.error_open_settings_button),
                    error = throwable,
                    moreHandler = {
                        val action = CoursesFragmentDirections.openSettings()
                        navController.navigate(action)
                    },
                    okHandler = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun errorFetchingAllCoursesFromOneCategory(category: Category, throwable: Throwable) {
        activity?.apply {
            if (throwable.isNetworkError()) {
                showNetworkError()
            } else {
                val navController = NavHostFragment.findNavController(this@CoursesFragment)
                showError(
                    message = getString(
                        R.string.error_fetching_all_courses_from_category,
                        category.slug
                    ),
                    moreTitle = getString(R.string.error_open_settings_button),
                    error = throwable,
                    moreHandler = {
                        val action = CoursesFragmentDirections.openSettings()
                        navController.navigate(action)
                    },
                    okHandler = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun errorCategoryNotFound() {
        activity?.apply {
            val navController = NavHostFragment.findNavController(this@CoursesFragment)
            showError(
                message = getString(R.string.error_category_not_found, categorySlug),
                moreTitle = getString(R.string.error_open_settings_button),
                moreHandler = {
                    val action = CoursesFragmentDirections.openSettings()
                    navController.navigate(action)
                },
                okHandler = {
                    navController.popBackStack()
                }
            )
        }
    }

    private fun goHome() {
        val navOptions = NavOptions.Builder().setLaunchSingleTop(true).build()
        val navController = NavHostFragment.findNavController(this)
        val action = CoursesFragmentDirections.openHome()
        navController.navigate(action, navOptions)
    }
}

class UsableOnTabListener(private val selectDelegate: (TabLayout.Tab) -> Unit) :
    TabLayout.BaseOnTabSelectedListener<TabLayout.Tab> {
    override fun onTabSelected(tab: TabLayout.Tab?) {
        tab?.apply { selectDelegate(this) }
    }

    override fun onTabReselected(tab: TabLayout.Tab?) {}

    override fun onTabUnselected(tab: TabLayout.Tab?) {}
}
