/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        darkBg: '#0F1219',
        darkSurf: '#171B26',
        darkBorder: '#252D3F',
        brandCyan: '#00E5FF',
        brandTeal: '#00E676',
        brandRed: '#FF5252',
        brandBlue: '#2979FF',
        mutedText: '#A0A5B0',
      },
      fontFamily: {
        sans: ['Outfit', 'Inter', 'system-ui', 'sans-serif'],
      },
      backdropBlur: {
        xs: '2px',
      }
    },
  },
  plugins: [],
}
