package org.mozilla.tryfox

object AppDeepLinkRouteMapper {
    fun routeFor(rawValue: String?): String? {
        return when (val destination = AppDeepLinkParser.parse(rawValue)) {
            is AppDeepLinkDestination.TreeherderSearch -> {
                AppRoutes.createTreeherderSearchRoute(
                    project = destination.project,
                    revision = destination.revision,
                )
            }

            is AppDeepLinkDestination.Profile -> {
                AppRoutes.createProfileByEmailRoute(destination.email)
            }

            null -> null
        }
    }
}
