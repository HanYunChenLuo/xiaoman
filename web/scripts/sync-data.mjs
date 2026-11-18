import { copyFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '../..')
const assets = resolve(root, 'app/src/main/assets')

mkdirSync(resolve(here, '../public/icons'), { recursive: true })
copyFileSync(resolve(root, 'data/brands.json'), resolve(here, '../public/brands.json'))
for (const name of ['logo_light', 'logo_dark']) {
  try {
    copyFileSync(resolve(root, `apps/android/app/src/main/assets/img/${name}.png`), resolve(here, `../public/${name}.png`))
  } catch {
    /* optional */
  }
}
try {
  copyFileSync(resolve(root, 'app/src/main/res/drawable/appicon.png'), resolve(here, '../public/favicon.png'))
} catch {
  /* optional */
}

const icons = {
  phone: 'apple/digital/Mobile-phone.png',
  laptop: 'apple/digital/Laptop.png',
  desktop: 'apple/digital/Desktop-computer.png',
  game: 'apple/digital/Video-game.png',
  drone: 'apple/digital/Movie-camera.png',
  camera: 'apple/digital/Camera.png',
  headphone: 'apple/digital/Headphone.png',
  watch: 'apple/digital/Watch.png',
  tv: 'apple/digital/Television.png',
  car: 'apple/vehicle/Automobile.png',
  bike: 'apple/vehicle/Bicycle.png',
  moto: 'apple/vehicle/Motorcycle.png',
  scooter: 'apple/vehicle/Kick-scooter.png',
  plane: 'apple/vehicle/Airplane.png',
  default: 'emoji/icon_default_object.png'
}
for (const [name, src] of Object.entries(icons)) {
  try {
    copyFileSync(resolve(assets, src), resolve(here, `../public/icons/${name}.png`))
  } catch {
    /* optional */
  }
}
