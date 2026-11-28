export interface LegalSection {
  id: string;
  number: string;
  heading: string;
  paragraphs?: string[];
  bullets?: Array<{ lead?: string; text: string }>;
  afterParagraphs?: string[];
  externalLink?: {
    prefix: string;
    label: string;
    href: string;
    suffix: string;
  };
  internalLink?: {
    prefix: string;
    label: string;
    route: 'privacy' | 'terms';
    suffix: string;
  };
  emailContact?: {
    prefix: string;
    email: string;
  };
}

export interface LegalDocument {
  route: 'privacy' | 'terms';
  eyebrow: string;
  title: string;
  lastUpdated: string;
  summary: string;
  intro: string;
  sections: LegalSection[];
}

export const PRIVACY_POLICY: LegalDocument = {
  route: 'privacy',
  eyebrow: 'Legal & Privacy',
  title: 'UniTask Privacy Policy',
  lastUpdated: '9 November 2026',
  summary:
    'Your courses, tasks and notes stay on your phone unless you choose to sign in or back up. UniTask does not sell your data and does not show ads. The Google Drive backup goes into a hidden folder in your own Drive that only UniTask can open.',
  intro:
    'UniTask ("the app") is an Android app for students to manage courses, assignments, deadlines, a class timetable and notes. This policy explains what the app stores, what it sends to other services, and the choices you have.',
  sections: [
    {
      id: 'phone-data',
      number: '01',
      heading: 'Data stored on your phone',
      paragraphs: [
        'The app stores the following on your device, in its private app storage:',
      ],
      bullets: [
        {
          text: 'Courses, timetable entries, assignments, your own tasks and notes (including note pictures).',
        },
        { text: 'Files you attach to assignments.' },
        {
          text: 'App settings such as theme, notification sound and reminder options.',
        },
      ],
      afterParagraphs: [
        'This data is not sent anywhere unless you use one of the optional features below. Uninstalling the app removes it from your phone.',
      ],
    },
    {
      id: 'google-signin',
      number: '02',
      heading: 'Sign in with Google (optional)',
      paragraphs: [
        'You can sign in with your Google account to use study groups and group tasks. When you sign in, the app receives your Google account email address and basic profile information from Google, and uses it to identify your account in the app.',
        'If you use these features, the data you add to them (for example group tasks) is stored in a cloud database provided by Supabase. Other members of a group can see what you share in that group. Your own courses, tasks, timetable and notes are not uploaded to Supabase. Sign-in is handled through Google.',
        'If you never sign in, none of this applies.',
      ],
    },
    {
      id: 'google-drive-backup',
      number: '03',
      heading: 'Google Drive backup (optional)',
      paragraphs: [
        'You can back up the app to your own Google Drive, and restore it on a new phone. To do this, the app asks for one Google permission: "See, create, and delete its own configuration data in your Google Drive" (drive.appdata).',
      ],
      bullets: [
        {
          text: 'The backup is stored in a hidden app-data folder in your Google Drive. Only UniTask can read or write this folder.',
        },
        {
          text: 'UniTask cannot see, open or change any of your other Google Drive files.',
        },
        {
          text: 'The backup contains your courses, timetable, assignments and their attachments, your own tasks, notes with pictures, and app settings.',
        },
        {
          text: 'The backup goes from your phone directly to your Google Drive. It is not sent to the developer, and the developer cannot read it.',
        },
        { text: 'The backup counts toward your Google Drive storage.' },
      ],
      externalLink: {
        prefix: "UniTask's use and transfer of information received from Google APIs adheres to the ",
        label: 'Google API Services User Data Policy',
        href: 'https://developers.google.com/terms/api-services-user-data-policy',
        suffix:
          ', including the Limited Use requirements. The information is used only to provide the backup and restore feature you asked for, is not used for advertising, and is not shared with anyone.',
      },
    },
    {
      id: 'updates-announcements',
      number: '04',
      heading: 'Checking for updates',
      paragraphs: [
        'The app checks GitHub for a newer release automatically when you open it (at most once every 10 minutes), and when you tap the update button. This is a normal web request to GitHub, which may see your IP address as any website would. No personal data from the app is included.',
        'About every 15 minutes, even when the app is closed, the app also contacts Supabase to check for new announcements and, if you are signed in, for new group tasks so it can show notifications.',
      ],
    },
    {
      id: 'permissions',
      number: '05',
      heading: 'Permissions the app may ask for',
      bullets: [
        {
          lead: 'Notifications and exact alarms:',
          text: 'to remind you about deadlines and classes on your phone.',
        },
        {
          lead: 'Internet:',
          text: 'for sign-in, groups, announcements, Google Drive backup and update checks.',
        },
        {
          lead: 'Camera:',
          text: 'only when you choose to scan a study group invite QR code. The camera image is read on your phone to find the code. It is not saved and not sent anywhere.',
        },
        {
          lead: 'Files you pick:',
          text: 'when you attach a file or open a file with the app, only the file you choose is read.',
        },
      ],
    },
    {
      id: 'sharing',
      number: '06',
      heading: 'Sharing',
      paragraphs: [
        'UniTask does not sell your data, does not show ads, and does not share your data with third parties for advertising. Your data is handled only by the services needed for the features you use: Google (sign-in and Google Drive) and Supabase (groups and announcements).',
      ],
    },
    {
      id: 'deleting-data',
      number: '07',
      heading: 'Your choices and deleting your data',
      bullets: [
        {
          lead: 'Phone data:',
          text: 'delete items inside the app, or uninstall the app.',
        },
        {
          lead: 'Google Drive backup:',
          text: "in the app, choose Disconnect. To delete the backup itself, open Google Drive, then Settings, Manage apps, UniTask, and choose Delete hidden app data. You can also remove UniTask's access in your Google Account under Security, then Third-party apps and services.",
        },
        {
          lead: 'Cloud data (groups):',
          text: 'in the app, open Settings, then Online account, then Delete Account. This permanently deletes your online account. Groups you own are handed to another member, or deleted if you are the only member. Group tasks and group courses you created are removed for everyone in those groups. Your courses, tasks and notes on this phone are not touched. You can also email the address below and ask for your data to be deleted. We will delete it.',
        },
      ],
    },
    {
      id: 'children',
      number: '08',
      heading: 'Children',
      paragraphs: [
        'UniTask is meant for students and is not directed at children under 13. We do not knowingly collect data from children under 13.',
      ],
    },
    {
      id: 'changes',
      number: '09',
      heading: 'Changes to this policy',
      paragraphs: [
        'If this policy changes, the new version will be posted on this page with a new date.',
      ],
    },
    {
      id: 'contact',
      number: '10',
      heading: 'Contact',
      emailContact: {
        prefix: 'Questions or deletion requests:',
        email: 'am9566222@gmail.com',
      },
    },
  ],
};

export const TERMS_OF_USE: LegalDocument = {
  route: 'terms',
  eyebrow: 'Legal & Terms',
  title: 'UniTask Terms of Use',
  lastUpdated: '10 November 2026',
  summary:
    'UniTask is a free student app made by one person. Use it fairly, keep your own backups, and understand it comes without warranties.',
  intro:
    'These terms apply to the UniTask Android app ("the app") and this website. By installing or using the app you agree to them. If you do not agree, please do not use the app.',
  sections: [
    {
      id: 'what-unitask-is',
      number: '01',
      heading: 'What UniTask is',
      paragraphs: [
        'UniTask helps students manage courses, assignments, deadlines, a class timetable and notes. Optional features include sign-in with Google, study groups with shared tasks, and backup to your own Google Drive. The app is free and has no ads.',
      ],
    },
    {
      id: 'account-content',
      number: '02',
      heading: 'Your account and your content',
      bullets: [
        {
          text: 'You can use most of the app without an account. Groups need a Google sign-in.',
        },
        {
          text: 'You are responsible for what you add to the app, including notes, attachments, and group tasks.',
        },
        {
          text: 'You keep ownership of your content. By sharing something in a study group, you allow other members of that group to see it.',
        },
        {
          text: 'Keep your Google account secure. Activity under your account is your responsibility.',
        },
      ],
    },
    {
      id: 'acceptable-use',
      number: '03',
      heading: 'Acceptable use',
      paragraphs: ['When using groups, do not:'],
      bullets: [
        {
          text: 'harass, threaten or abuse other people, or share hateful or illegal content;',
        },
        {
          text: "share other people's private information without their permission;",
        },
        {
          text: 'upload malware or try to break, overload or gain unauthorised access to the service;',
        },
        {
          text: "use the app to cheat in ways that break your university's rules. How you use it for your studies is your own responsibility.",
        },
      ],
      afterParagraphs: [
        'Group owners can remove members. The developer may remove content or disable accounts that break these terms or harm the service.',
      ],
    },
    {
      id: 'drive-backup',
      number: '04',
      heading: 'Google Drive backup',
      paragraphs: [
        'Backup uses a hidden app-data folder in your own Google Drive and counts toward your Drive storage. You are responsible for keeping a working backup of anything important.',
      ],
      internalLink: {
        prefix: 'See the ',
        label: 'Privacy Policy',
        route: 'privacy',
        suffix: ' for details.',
      },
    },
    {
      id: 'reminders',
      number: '05',
      heading: 'Reminders and notifications',
      paragraphs: [
        'Reminders depend on your phone. Battery saver settings, phone brand restrictions, denied permissions or a switched-off phone can delay or stop them. Do not rely on UniTask as your only way to remember an important deadline.',
      ],
    },
    {
      id: 'open-source',
      number: '06',
      heading: 'Open source',
      externalLink: {
        prefix: 'The source code is on ',
        label: 'GitHub',
        href: 'https://github.com/ahmad-maqsood/unitask',
        suffix:
          ' and is released under the MIT License. The license covers the code. These terms cover your use of the app and the online services run for it.',
      },
    },
    {
      id: 'no-warranty',
      number: '07',
      heading: 'No warranty',
      paragraphs: [
        'The app is provided "as is" and "as available", without warranties of any kind. I do not promise that it will be error free, always available, or that data will never be lost. Features may change or be removed at any time, including the online services.',
      ],
    },
    {
      id: 'liability',
      number: '08',
      heading: 'Limit of liability',
      paragraphs: [
        'To the extent the law allows, the developer is not liable for missed deadlines, lost data, or any indirect or consequential loss that comes from using or being unable to use the app.',
      ],
    },
    {
      id: 'ending-use',
      number: '09',
      heading: 'Ending your use',
      paragraphs: [
        'You can stop using the app at any time by uninstalling it. To delete your online account, open Settings, then Online account, then Delete Account.',
      ],
      internalLink: {
        prefix: 'See the ',
        label: 'Privacy Policy',
        route: 'privacy',
        suffix: ' for what is deleted.',
      },
    },
    {
      id: 'changes',
      number: '10',
      heading: 'Changes to these terms',
      paragraphs: [
        'If these terms change, the new version will be posted on this page with a new date. Continuing to use the app after a change means you accept the new terms.',
      ],
    },
    {
      id: 'contact',
      number: '11',
      heading: 'Contact',
      emailContact: {
        prefix: 'Questions:',
        email: 'am9566222@gmail.com',
      },
    },
  ],
};
