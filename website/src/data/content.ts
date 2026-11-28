export const REPO_OWNER = 'ahmad-maqsood';
export const REPO_NAME = 'unitask';
export const REPO_URL = `https://github.com/${REPO_OWNER}/${REPO_NAME}`;
export const ISSUES_URL = `${REPO_URL}/issues`;
export const RELEASES_URL = `${REPO_URL}/releases`;
export const LICENSE_URL = `${REPO_URL}/blob/main/LICENSE`;
export const VERIFICATION_DOC_URL = `${REPO_URL}/blob/main/docs/RELEASE-VERIFICATION.md`;

export interface ProductScreen {
  id: string;
  label: string;
  title: string;
  caption: string;
  file: string;
  alt: string;
}

export const PRODUCT_SCREENS: ProductScreen[] = [
  {
    id: 'deadlines',
    label: 'Deadlines',
    title: 'Deadlines & daily overview',
    caption:
      'Assignments grouped by urgency with Pending, Done, and Due Today counters, priority tags, and swipe-to-complete.',
    file: 'screenshots/deadlines.webp',
    alt: 'UniTask Deadlines screen showing upcoming university assignments grouped by urgency with priority indicators',
  },
  {
    id: 'timetable',
    label: 'Timetable',
    title: 'Weekly & bi-weekly timetable',
    caption:
      'Supports Week A and Week B schedules with a live current and next class card, clash alerts, and one-tap .ics export.',
    file: 'screenshots/timetable.webp',
    alt: 'UniTask Timetable screen displaying a weekly class schedule with colour-coded courses',
  },
  {
    id: 'notes',
    label: 'Notes',
    title: 'Course-linked Markdown notes',
    caption:
      'Edit and Preview modes with interactive checklists, syntax-highlighted code blocks, [[ backlinks, folders, and PDF export.',
    file: 'screenshots/note_preview.webp',
    alt: 'UniTask Note preview screen rendering formatted Markdown with checklists and syntax highlighting',
  },
  {
    id: 'timer',
    label: 'Study timer',
    title: 'Stopwatch, countdown & Pomodoro',
    caption:
      'Configurable focus and break rounds with background countdown alerts, fullscreen landscape mode, and auto-start options.',
    file: 'screenshots/timer_pomodoro.webp',
    alt: 'UniTask Pomodoro study timer screen showing active focus round controls',
  },
  {
    id: 'courses',
    label: 'Courses',
    title: 'Colour-coded courses',
    caption:
      'Store credit hours, instructors, and colour tags that automatically flow into deadlines, timetable slots, and notes.',
    file: 'screenshots/courses.webp',
    alt: 'UniTask Courses screen listing university modules with credit hours and colour coding',
  },
  {
    id: 'groups',
    label: 'Groups',
    title: 'Optional shared study groups',
    caption:
      'Join with a QR code, invite link, or 8-character code for shared tasks, @all tagging, and personal completion ticks.',
    file: 'screenshots/groups.webp',
    alt: 'UniTask Study groups screen showing shared study spaces for collaborative coursework',
  },
  {
    id: 'progress',
    label: 'Progress',
    title: 'Weekly progress & streaks',
    caption:
      'Review tasks finished this week versus last week, your daily streak, weekly history bars, and completion per course.',
    file: 'screenshots/progress.webp',
    alt: 'UniTask Progress screen showing completed tasks this week, daily streak, and course completion bars',
  },
  {
    id: 'search',
    label: 'Search',
    title: 'Global search across everything',
    caption:
      'Jump straight to matching assignments, Markdown notes, and courses from a single unified search view.',
    file: 'screenshots/global_search.webp',
    alt: 'UniTask Global search screen returning matching tasks, notes, and courses together',
  },
];

export type CapabilityIconName =
  | 'calendar-check'
  | 'file-text'
  | 'clock'
  | 'timer'
  | 'bell-ring'
  | 'shield-check'
  | 'users'
  | 'trending-up';

export interface CapabilityCard {
  id: string;
  icon: CapabilityIconName;
  title: string;
  description: string;
  span: 'wide' | 'standard';
}

export const CAPABILITIES: CapabilityCard[] = [
  {
    id: 'deadlines-coursework',
    icon: 'calendar-check',
    title: 'Deadlines, courses & repeating tasks',
    description:
      'Link assignments with priorities, subtask checklists, and offline file attachments up to 25 MB to colour-coded courses, with daily, weekday, weekly, or monthly repeats.',
    span: 'wide',
  },
  {
    id: 'markdown-notes',
    icon: 'file-text',
    title: 'Markdown notes, folders & backlinks',
    description:
      'Write course-tagged notes with live Preview, syntax-highlighted code blocks, [[ note links, coloured folders, ZIP or PDF export, and a 30-day trash bin.',
    span: 'wide',
  },
  {
    id: 'timetable',
    icon: 'clock',
    title: 'Week A / Week B timetable',
    description:
      'Follow weekly or bi-weekly class rotations with a live next-class card, clash warnings, free-time gaps, and one-tap .ics calendar export.',
    span: 'standard',
  },
  {
    id: 'study-timer',
    icon: 'timer',
    title: 'Stopwatch, countdown & Pomodoro',
    description:
      'Run adjustable focus and break rounds or custom countdowns that notify you when time is up even if UniTask is closed.',
    span: 'standard',
  },
  {
    id: 'reminders-widget',
    icon: 'bell-ring',
    title: 'Exact reminders & Today widget',
    description:
      'Schedule exact AlarmManager alerts with Mark as Done and Snooze actions, missed-reminder recovery, a built-in reminder health check, and the UniTask Today Glance widget.',
    span: 'wide',
  },
  {
    id: 'private-backups',
    icon: 'shield-check',
    title: 'Private Google Drive & encrypted local backups',
    description:
      'Back up courses, tasks, notes, and attachments to a hidden Google Drive appdata folder using two rotating copies, or export password-protected offline backups.',
    span: 'wide',
  },
  {
    id: 'study-groups',
    icon: 'users',
    title: 'Optional real-time study groups',
    description:
      'Sign in with Google only if you want shared group spaces joined via QR code, invite link, or 8-character code over Supabase Realtime.',
    span: 'standard',
  },
  {
    id: 'progress-my-tasks',
    icon: 'trending-up',
    title: 'Progress stats & My Tasks',
    description:
      'Keep non-coursework to-dos separate in My Tasks while tracking weekly completion bars, daily streaks, and per-course progress.',
    span: 'standard',
  },
];

export interface AppearanceView {
  id: string;
  label: string;
  title: string;
  description: string;
  file: string;
  alt: string;
}

export const APPEARANCE_VIEWS: AppearanceView[] = [
  {
    id: 'dark',
    label: 'Dark theme',
    title: 'Comfortable dark mode',
    description:
      'Cool dark surfaces tuned for late-night study sessions, with every foreground and background pair checked against WCAG AA contrast.',
    file: 'screenshots/theme_dark.webp',
    alt: 'UniTask Deadlines screen rendered in dark mode with Indigo accent',
  },
  {
    id: 'light',
    label: 'Light theme',
    title: 'Crisp daytime clarity',
    description:
      'Follows Android system appearance automatically or switches manually between Light and Dark in General settings.',
    file: 'screenshots/deadlines.webp',
    alt: 'UniTask Deadlines screen rendered in light mode with default Indigo accent',
  },
  {
    id: 'accent',
    label: 'Accent colours',
    title: 'Eleven curated accent palettes',
    description:
      'Every accent defines dedicated light and dark Material 3 schemes and updates the UniTask Today home-screen widget to match. Shown here in Rose.',
    file: 'screenshots/theme_accent.webp',
    alt: 'UniTask Deadlines screen showcasing the Rose accent colour theme',
  },
  {
    id: 'general',
    label: 'General settings',
    title: 'Language, sounds & themed icons',
    description:
      'Choose English or Urdu (with full right-to-left layout), pick custom notification sounds per channel, and use Android 13+ themed icons.',
    file: 'screenshots/settings_general.webp',
    alt: 'UniTask General settings screen displaying theme mode, accent colour, and language preferences',
  },
];

export interface AccentPaletteTokens {
  primary: string;
  onPrimary: string;
  hover: string;
  text: string;
  container: string;
  onContainer: string;
}

export interface AccentSwatch {
  id: string;
  name: string;
  hex: string;
  light: AccentPaletteTokens;
  dark: AccentPaletteTokens;
}

/** Exact 11 accent themes defined in app/src/main/java/com/example/ui/theme/AccentThemes.kt */
export const ACCENT_SWATCHES: AccentSwatch[] = [
  {
    id: 'indigo',
    name: 'Indigo',
    hex: '#4F46E5',
    light: {
      primary: '#4F46E5',
      onPrimary: '#FFFFFF',
      hover: '#4338CA',
      text: '#4F46E5',
      container: '#E3E6FF',
      onContainer: '#1E1B4B',
    },
    dark: {
      primary: '#6366F1',
      onPrimary: '#FFFFFF',
      hover: '#5558EB',
      text: '#9AA8FF',
      container: '#2A2F63',
      onContainer: '#DDE2FF',
    },
  },
  {
    id: 'ocean',
    name: 'Ocean teal',
    hex: '#157984',
    light: {
      primary: '#157984',
      onPrimary: '#FFFFFF',
      hover: '#11636D',
      text: '#157984',
      container: '#DEF9FC',
      onContainer: '#104146',
    },
    dark: {
      primary: '#157984',
      onPrimary: '#FFFFFF',
      hover: '#198E9B',
      text: '#24C9DB',
      container: '#224E53',
      onContainer: '#D6FBFF',
    },
  },
  {
    id: 'forest',
    name: 'Forest green',
    hex: '#157F46',
    light: {
      primary: '#157F46',
      onPrimary: '#FFFFFF',
      hover: '#11693A',
      text: '#157F46',
      container: '#DEFCEC',
      onContainer: '#10462A',
    },
    dark: {
      primary: '#157F46',
      onPrimary: '#FFFFFF',
      hover: '#199653',
      text: '#22D375',
      container: '#225339',
      onContainer: '#D6FFE9',
    },
  },
  {
    id: 'rose',
    name: 'Rose',
    hex: '#D32263',
    light: {
      primary: '#D32263',
      onPrimary: '#FFFFFF',
      hover: '#B61B54',
      text: '#D32263',
      container: '#FCDEE9',
      onContainer: '#461024',
    },
    dark: {
      primary: '#D32263',
      onPrimary: '#FFFFFF',
      hover: '#E23273',
      text: '#EB82A8',
      container: '#532234',
      onContainer: '#FFD6E5',
    },
  },
  {
    id: 'sunset',
    name: 'Sunset orange',
    hex: '#B2531D',
    light: {
      primary: '#B2531D',
      onPrimary: '#FFFFFF',
      hover: '#964416',
      text: '#B2531D',
      container: '#FCE9DE',
      onContainer: '#462410',
    },
    dark: {
      primary: '#B2531D',
      onPrimary: '#FFFFFF',
      hover: '#C86025',
      text: '#E69668',
      container: '#533422',
      onContainer: '#FFE5D6',
    },
  },
  {
    id: 'violet',
    name: 'Violet',
    hex: '#8F47E1',
    light: {
      primary: '#8F47E1',
      onPrimary: '#FFFFFF',
      hover: '#7935C9',
      text: '#8F47E1',
      container: '#ECDEFC',
      onContainer: '#2A1046',
    },
    dark: {
      primary: '#8F47E1',
      onPrimary: '#FFFFFF',
      hover: '#9F5BE9',
      text: '#B88BEC',
      container: '#392253',
      onContainer: '#E9D6FF',
    },
  },
  {
    id: 'cobalt',
    name: 'Cobalt blue',
    hex: '#1653C5',
    light: {
      primary: '#1653C5',
      onPrimary: '#FFFFFF',
      hover: '#1244A3',
      text: '#1653C5',
      container: '#DAE6FB',
      onContainer: '#112445',
    },
    dark: {
      primary: '#2563EB',
      onPrimary: '#FFFFFF',
      hover: '#3B74F0',
      text: '#5187EC',
      container: '#263859',
      onContainer: '#D8E5FD',
    },
  },
  {
    id: 'amber',
    name: 'Golden amber',
    hex: '#B07408',
    light: {
      primary: '#9D6A04',
      onPrimary: '#FFFFFF',
      hover: '#825703',
      text: '#9D6A04',
      container: '#FBF0DA',
      onContainer: '#453411',
    },
    dark: {
      primary: '#B07408',
      onPrimary: '#FFFFFF',
      hover: '#C7850C',
      text: '#F9B11F',
      container: '#594826',
      onContainer: '#FDF1D8',
    },
  },
  {
    id: 'graphite',
    name: 'Graphite',
    hex: '#454F5F',
    light: {
      primary: '#454F5F',
      onPrimary: '#FFFFFF',
      hover: '#37404E',
      text: '#454F5F',
      container: '#DAE8FB',
      onContainer: '#112745',
    },
    dark: {
      primary: '#526074',
      onPrimary: '#FFFFFF',
      hover: '#617188',
      text: '#9BA9BD',
      container: '#263B59',
      onContainer: '#D8E8FD',
    },
  },
  {
    id: 'mocha',
    name: 'Mocha',
    hex: '#7B4D32',
    light: {
      primary: '#7B4D32',
      onPrimary: '#FFFFFF',
      hover: '#653E28',
      text: '#7B4D32',
      container: '#FBE6DA',
      onContainer: '#3B2214',
    },
    dark: {
      primary: '#7B4D32',
      onPrimary: '#FFFFFF',
      hover: '#905B3C',
      text: '#BC7F5C',
      container: '#593926',
      onContainer: '#FDE6D8',
    },
  },
  {
    id: 'plum',
    name: 'Plum',
    hex: '#8E2A7C',
    light: {
      primary: '#8E2A7C',
      onPrimary: '#FFFFFF',
      hover: '#762267',
      text: '#8E2A7C',
      container: '#FBDAF3',
      onContainer: '#451139',
    },
    dark: {
      primary: '#8E2A7C',
      onPrimary: '#FFFFFF',
      hover: '#A63491',
      text: '#E48AD2',
      container: '#582451',
      onContainer: '#FFD6F5',
    },
  },
];

export interface PermissionRow {
  name: string;
  scope: 'Core' | 'Optional';
  purpose: string;
}

export const ANDROID_PERMISSIONS: PermissionRow[] = [
  {
    name: 'POST_NOTIFICATIONS',
    scope: 'Core',
    purpose: 'Deadline, class, daily summary, and announcement alerts.',
  },
  {
    name: 'USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM',
    scope: 'Core',
    purpose: 'Fires reminders at the exact scheduled minute (auto-granted on Android 13+).',
  },
  {
    name: 'RECEIVE_BOOT_COMPLETED',
    scope: 'Core',
    purpose: 'Re-schedules your saved alarms after a phone restart.',
  },
  {
    name: 'REQUEST_IGNORE_BATTERY_OPTIMIZATIONS',
    scope: 'Optional',
    purpose: 'Opens Android battery settings when you choose Background setup for reliable alarms.',
  },
  {
    name: 'VIBRATE',
    scope: 'Core',
    purpose: 'Alarm-style and Pomodoro round vibration alerts.',
  },
  {
    name: 'CAMERA',
    scope: 'Optional',
    purpose: 'Only requested if you scan a study group QR code; the app works completely without it.',
  },
  {
    name: 'INTERNET & ACCESS_NETWORK_STATE',
    scope: 'Optional',
    purpose: 'Used for optional Google Drive backup, study groups, GitHub update checks, and announcements.',
  },
];

export const INSTALL_STEPS = [
  {
    step: '01',
    title: 'Download the signed APK',
    detail:
      'Tap Download APK above to fetch the latest signed release directly from GitHub Releases.',
  },
  {
    step: '02',
    title: 'Install on your Android device',
    detail:
      'Open the downloaded .apk file from your browser or file manager. If Android asks, allow installing unknown apps from that app.',
  },
  {
    step: '03',
    title: 'Stay updated in-app',
    detail:
      'Use the top-bar update button or Settings → About and help → Check for updates. Every release is signed with the same key so updates install cleanly.',
  },
];
