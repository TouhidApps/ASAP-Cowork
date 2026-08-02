/**
 * A guided tour of what the agent platform can do, grouped by lifecycle
 * stage (matches PLAN.md's phased roadmap) rather than by internal agent
 * name — someone opening this menu is asking "what can I ask for", not
 * "which agent handles this". Each prompt is phrased the way a user would
 * actually type it, not a feature label, since the point is to show *how*
 * to ask, not just *what* exists.
 */
export interface ExampleCategory {
  title: string
  prompts: string[]
}

export const EXAMPLE_CATEGORIES: ExampleCategory[] = [
  {
    title: 'Plan & design',
    prompts: [
      'Turn my idea for a workout tracking app into user stories and scope',
      'Recommend an architecture pattern for a note-taking app',
      'Suggest a tech stack for a cross-platform e-commerce app',
    ],
  },
  {
    title: 'Start a new project',
    prompts: [
      'Make a new Android app called PlantCare',
      'Create KMP/CMP project with shared UI & Logic',
      'Create a Flutter app for tracking expenses',
      'Set up a React Native app with navigation',
      'Scaffold a Spring Boot backend with MySQL',
    ],
  },
  {
    title: 'Build & run',
    prompts: [
      'Build and run the app on the emulator',
      'Install the app on my connected device',
      'Take a screenshot of the app on the connected device',
      'Record a video of the app running',
    ],
  },
  {
    title: 'Test & debug',
    prompts: [
      'Write unit tests for the login screen',
      'Read the device logs and tell me why the app crashed',
      'Debug why the build is failing',
    ],
  },
  {
    title: 'Ship it',
    prompts: [
      'Set up a CI/CD pipeline for this project',
      'Upload this build to Firebase App Distribution',
      'Prepare this app for the Google Play Console',
      'Generate a logo and app icon',
      'Write a privacy policy and terms of service',
      'Build a landing page for this app',
      'Convert these screenshots into Play Store listing images',
    ],
  },
  {
    title: 'Maintain & improve',
    prompts: [
      'Generate a README for this project',
      'Wire up Firebase Analytics',
      'Scan this project for security issues and leaked secrets',
      'Check how fast the app starts and how much memory it uses',
    ],
  },
]
