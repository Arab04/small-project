/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Background
        ink: {
          950: '#08080b',
          900: '#0a0a0d',
          850: '#0c0c10',
          800: '#0e0e12',
          700: '#1a1a22',
          600: '#2a2a32',
          500: '#3a3a45',
          400: '#56565e',
          300: '#6d6d75',
          200: '#9999a1',
          100: '#d4d4d8',
          50: '#f5f5f7',
        },
        // Accent: electric lime — sport energy
        lime: {
          electric: '#c5ff50',
          dim: '#8c9c5a',
        },
        // Warning: coral — fatigue, danger
        coral: '#ff7a6b',
        amber: '#ffb155',
        // Reactive blue — away team
        sky: {
          electric: '#6bb4ff',
        },
      },
      fontFamily: {
        sans: ['-apple-system', 'BlinkMacSystemFont', 'SF Pro Display', 'system-ui', 'sans-serif'],
        mono: ['ui-monospace', 'SF Mono', 'Menlo', 'monospace'],
      },
      fontSize: {
        '2xs': ['10px', '14px'],
      },
    },
  },
  plugins: [],
};
