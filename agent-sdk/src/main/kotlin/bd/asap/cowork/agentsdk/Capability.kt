package bd.asap.cowork.agentsdk

/**
 * A capability tag an agent declares and a task requests. Plain string-backed
 * rather than an enum so new agents (and their capabilities) can be added as
 * new modules without ever touching this file.
 */
@JvmInline
value class Capability(val id: String) {
    companion object {
        val REQUIREMENTS = Capability("requirements")
        val ARCHITECTURE = Capability("architecture")
        val TECH_STACK = Capability("tech_stack")
        val SCAFFOLDING = Capability("scaffolding")
        val ANDROID_BUILD = Capability("android.build")
        val IOS_BUILD = Capability("ios.build")
        val KMP_BUILD = Capability("kmp.build")
        val FLUTTER_BUILD = Capability("flutter.build")
        val REACT_NATIVE_BUILD = Capability("react_native.build")
        val BACKEND_BUILD = Capability("backend.build")
        val TEST_UNIT = Capability("testing.unit")
        val DEBUG = Capability("debugging")
        val CICD = Capability("cicd")
        val PUBLISH = Capability("publish")
        val BRANDING = Capability("branding")
        val LEGAL = Capability("legal")
        val LANDING_PAGE = Capability("landing_page")
        val STORE_ASSETS = Capability("store_assets")
        val DOCUMENTATION = Capability("documentation")
        val ANALYTICS = Capability("analytics")
        val SECURITY = Capability("security")
        val PERFORMANCE = Capability("performance")
        val NOTES = Capability("notes")
    }
}
