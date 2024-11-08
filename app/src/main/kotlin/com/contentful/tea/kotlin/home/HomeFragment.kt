package com.contentful.tea.kotlin.home

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.fragment.NavHostFragment
import com.contentful.tea.kotlin.BuildConfig
import com.contentful.tea.kotlin.R
import com.contentful.tea.kotlin.Reloadable
import com.contentful.tea.kotlin.content.Api
import com.contentful.tea.kotlin.content.ContentInfrastructure
import com.contentful.tea.kotlin.content.EditorialFeature
import com.contentful.tea.kotlin.content.Layout
import com.contentful.tea.kotlin.content.LayoutModule
import com.contentful.tea.kotlin.content.Parameter
import com.contentful.tea.kotlin.dependencies.Dependencies
import com.contentful.tea.kotlin.dependencies.DependenciesProvider
import com.contentful.tea.kotlin.extensions.isNetworkError
import com.contentful.tea.kotlin.extensions.setImageResourceFromUrl
import com.contentful.tea.kotlin.extensions.showError
import com.contentful.tea.kotlin.extensions.showNetworkError
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * This fragment will be the actual starting point of the app: Showing modules and offering
 * settings.
 */
class HomeFragment : Fragment(), Reloadable {

    private lateinit var dependencies: Dependencies
    private lateinit var mainBottomNavigation: BottomNavigationView
    private lateinit var homeCourses: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        if (activity !is DependenciesProvider) {
            throw IllegalStateException("Activity must implement Dependency provider.")
        }

        dependencies = (activity as DependenciesProvider).dependencies()

        val view = inflater.inflate(R.layout.fragment_home, container, false)
        mainBottomNavigation = view.findViewById(R.id.main_bottom_navigation)
        homeCourses = view.findViewById(R.id.home_courses)

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainBottomNavigation.setOnNavigationItemSelectedListener {
            if (activity != null) {
                bottomNavigationItemSelected(it)
                true
            } else {
                false
            }
        }

        activity?.apply {
            val preferences =
                getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)
            dependencies.contentInfrastructure.applyParameterFromSharedPreferences(preferences) {
                loadHomeView()
            }
        }
    }

    override fun onResume() {
        activity?.findViewById<Toolbar>(R.id.main_toolbar)?.findViewById<View>(R.id.logo_image)
            ?.setOnClickListener { navigateUp() }

        super.onResume()
    }

    private fun loadHomeView() {
        dependencies
            .contentInfrastructure
            .fetchHomeLayout(errorCallback = ::errorFetchingLayout) { layout: Layout ->
                layout.contentModules.forEach { module ->
                    activity?.runOnUiThread {
                        layoutInflater.inflate(R.layout.course_card, homeCourses, false)
                            .apply {
                                updateModuleView(this, module)
                                homeCourses.addView(this)
                            }
                    }
                }
            }
    }

    private fun updateModuleView(view: View, module: LayoutModule) {
        val parser = dependencies.markdown

        when (module) {
            is LayoutModule.HightlightedCourse -> {
                val cardTitle = view.findViewById<TextView>(R.id.card_title)
                val cardDescription = view.findViewById<TextView>(R.id.card_description)
                val cardBackground = view.findViewById<ImageView>(R.id.card_background)
                val cardCallToAction = view.findViewById<Button>(R.id.card_call_to_action)

                cardTitle.text = parser.parse(module.course.title)
                cardDescription.text = parser.parse(module.course.shortDescription)
                cardBackground.setImageResourceFromUrl(module.course.image)

                val l: (View) -> Unit = {
                    val navController = NavHostFragment.findNavController(this@HomeFragment)
                    val action = HomeFragmentDirections.openCourseOverview(module.course.slug)
                    navController.navigate(action)
                }

                view.setOnClickListener(l)
                cardCallToAction.visibility = View.VISIBLE
                cardCallToAction.setOnClickListener(l)
            }

            is LayoutModule.HeroImage -> {
                val cardTitle = view.findViewById<TextView>(R.id.card_title)
                val cardBackground = view.findViewById<ImageView>(R.id.card_background)
                val cardScrim = view.findViewById<View>(R.id.card_scrim)
                val cardCallToAction = view.findViewById<Button>(R.id.card_call_to_action)

                cardTitle.text = parser.parse(module.title)
                cardBackground.setImageResourceFromUrl(module.backgroundImage)
                cardScrim.setBackgroundResource(android.R.color.transparent)
                cardCallToAction.visibility = View.GONE
            }

            is LayoutModule.Copy -> {
                val cardTitle = view.findViewById<TextView>(R.id.card_title)
                val cardDescription = view.findViewById<TextView>(R.id.card_description)
                val cardBackground = view.findViewById<ImageView>(R.id.card_background)
                val cardScrim = view.findViewById<View>(R.id.card_scrim)
                val cardCallToAction = view.findViewById<Button>(R.id.card_call_to_action)

                cardTitle.text = parser.parse(module.headline)
                cardDescription.text = parser.parse(module.copy)
                cardBackground.setBackgroundResource(android.R.color.transparent)
                cardScrim.setBackgroundResource(android.R.color.transparent)

                cardCallToAction.visibility = View.VISIBLE
                cardCallToAction.text = module.ctaTitle
                cardCallToAction.setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(module.ctaLink)))
                }
            }
        }
    }

    private fun bottomNavigationItemSelected(item: MenuItem): Boolean {
        val navController = Navigation.findNavController(
            requireActivity(), R.id.navigation_host_fragment
        )
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

    private fun errorFetchingLayout(throwable: Throwable) {
        activity?.apply {
            if (throwable.isNetworkError()) {
                showNetworkError()
            } else {
                val navController = NavHostFragment.findNavController(this@HomeFragment)
                showError(
                    message = getString(R.string.error_fetching_layout),
                    moreTitle = getString(R.string.error_open_settings_button),
                    error = throwable,
                    moreHandler = {
                        val action = HomeFragmentDirections.openSettings()
                        navController.navigate(action)
                    },
                    okHandler = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }

    private fun ContentInfrastructure.applyParameterFromSharedPreferences(
        preferences: SharedPreferences,
        successCallback: () -> Unit
    ) {
        val parameter = Parameter(
            editorialFeature =
            if (preferences.getBoolean(getString(R.string.settings_key_editorial), false)) {
                EditorialFeature.Enabled
            } else {
                EditorialFeature.Disabled
            },
            api = Api.valueOf(
                preferences.getString(
                    getString(R.string.settings_key_api),
                    Api.CDA.name
                )!!
            ),
            locale = preferences.getString(getString(R.string.settings_key_locale), "en-US")!!,
            spaceId = preferences.getString(
                getString(R.string.settings_key_space_id),
                BuildConfig.CONTENTFUL_SPACE_ID
            )!!,
            deliveryToken = preferences.getString(
                getString(R.string.settings_key_delivery_token),
                BuildConfig.CONTENTFUL_DELIVERY_TOKEN
            )!!,
            previewToken = preferences.getString(
                getString(R.string.settings_key_preview_token),
                BuildConfig.CONTENTFUL_PREVIEW_TOKEN
            )!!,
            host = preferences.getString(
                getString(R.string.settings_key_host),
                BuildConfig.CONTENTFUL_HOST
            )!!
        )

        applyParameter(
            parameter,
            errorHandler = { },
            successHandler = { space ->
                Log.d(
                    "HomeFragment.kt",
                    getString(
                        R.string.settings_connected_successfully_to_space,
                        space.name()
                    )
                )

                successCallback()
            }
        )
    }

    private fun navigateUp() {}

    override fun reload() {
        homeCourses.removeAllViews()
        loadHomeView()
    }
}