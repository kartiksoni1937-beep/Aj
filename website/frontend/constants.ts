
import { FAQItem, FeatureItem } from './types';

export const FAQ_DATA: FAQItem[] = [
  {
    id: '1',
    question: 'What is Echo?',
    answer: 'Echo Client is a Minecraft client built for the latest versions. It focuses on modern combat and PvP, including spears, maces, and other niche modes. Built by Cyde.'
  },
  {
    id: '2',
    question: 'Is Echo Client free?',
    answer: 'Echo is a premium paid Minecraft client. Maintaining a free client with high quality updates and support is not feasible long-term.'
  },
  {
    id: '3',
    question: 'What versions are supported?',
    answer: 'Echo is focused on supporting every Minecraft version past 1.21.11. We aim to fastly support future updates as they are released.'
  },
  {
    id: '4',
    question: 'Is it undetectable?',
    answer: 'Echo focuses on "Ghost" modules, prioritizing undetectability on major servers while providing a competitive edge. We target popular anti-cheats to ensure our users can use the client with confidence.'
  }
];

export const FEATURES_DATA: FeatureItem[] = [
  {
    title: 'Spear Dominance',
    description: 'First-to-market reach and aim assist modules specifically tuned for the new Tridents and Spears in 1.21.11.',
    icon: 'spear',
    colSpan: 2
  },
  {
    title: 'Mace Heavy Impact',
    description: 'Calculated critical smash attacks with the Mace. Never miss a crush capability.',
    icon: 'hammer',
    colSpan: 1
  },
  {
    title: 'Ghost Essentials',
    description: 'Includes highly customizable Autoclicker, Reach, Velocity, and Hitbox expanders designed to bypass modern anti-cheats.',
    icon: 'ghost',
    colSpan: 1
  },
  {
    title: 'Visuals & HUD',
    description: 'A fully customizable, hardware-accelerated GUI that looks as good as it performs.',
    icon: 'eye',
    colSpan: 2
  }
];
