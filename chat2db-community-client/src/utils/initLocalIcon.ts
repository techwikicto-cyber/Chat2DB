import { isDevelopment } from './env';

// Webpack replaces this with the runtime public path, which differs per build:
// '/' in dev, './' in the desktop package, '/static/front/' on the web server.
declare const __webpack_public_path__: string;

const MONOCHROME_ICONFONT_SCRIPT_ID = 'chat2db-monochrome-iconfont';
// Must be resolved against the public path, not the page URL. As a bare
// relative path it resolved against the current route - so the web build asked
// for /iconfont/iconfont.js, got a 404, and rendered no monochrome icons at all.
const MONOCHROME_ICONFONT_SCRIPT = `${__webpack_public_path__}iconfont/iconfont.js`;
const COLOR_ICONFONT_SCRIPT_ID = 'chat2db-color-iconfont';
const COLOR_ICONFONT_SCRIPT = '//at.alicdn.com/t/c/font_4551262_fnn84ra2j4v.js';

const appendIconfontScript = (id: string, src: string) => {
  if (document.getElementById(id)) {
    return;
  }

  const script = document.createElement('script');
  script.id = id;
  script.src = src;
  script.async = true;
  document.body.appendChild(script);
};

export const initializeDevEnvironmentIcon = () => {
  // The monochrome icon font must remain available offline in the desktop package.
  appendIconfontScript(MONOCHROME_ICONFONT_SCRIPT_ID, MONOCHROME_ICONFONT_SCRIPT);

  if (isDevelopment) {
    // color
    appendIconfontScript(COLOR_ICONFONT_SCRIPT_ID, COLOR_ICONFONT_SCRIPT);
  }
};
