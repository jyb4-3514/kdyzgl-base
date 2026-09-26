import {
  Button,
  Calendar,
  Cell,
  CellGroup,
  Checkbox,
  Empty,
  Field,
  Form,
  Grid,
  GridItem,
  Icon,
  List,
  Loading,
  NavBar,
  NoticeBar,
  Picker,
  Popup,
  Progress,
  PullRefresh,
  Radio,
  RadioGroup,
  Search,
  Skeleton,
  Stepper,
  Switch,
  Tab,
  Tabbar,
  TabbarItem,
  Tabs,
  Tag,
  TimePicker
} from 'vant'

/**
 * Vant 4 按需引入收口
 *
 * 为什么不用 unplugin-vue-components + VantResolver（官方推荐方案）：本轮不允许改动构建依赖，
 * 手写注册表配合 Vant 4 的 ESM 产物同样能 tree-shaking，样式按组件逐个引入（官方《快速上手-手动引入》）。
 * 为什么样式不引 vant/lib/index.css：整包 CSS 会把未使用组件的样式一并打进移动端产物。
 * 说明：Badge 未显式注册 —— TabbarItem / GridItem / Tag 的样式入口已包含 badge 样式，重复引入无收益。
 */
import 'vant/es/button/style/index'
import 'vant/es/calendar/style/index'
import 'vant/es/cell/style/index'
import 'vant/es/cell-group/style/index'
import 'vant/es/checkbox/style/index'
import 'vant/es/dialog/style/index'
import 'vant/es/empty/style/index'
import 'vant/es/field/style/index'
import 'vant/es/form/style/index'
import 'vant/es/grid/style/index'
import 'vant/es/grid-item/style/index'
import 'vant/es/icon/style/index'
import 'vant/es/list/style/index'
import 'vant/es/loading/style/index'
import 'vant/es/nav-bar/style/index'
import 'vant/es/notice-bar/style/index'
import 'vant/es/picker/style/index'
import 'vant/es/popup/style/index'
import 'vant/es/progress/style/index'
import 'vant/es/pull-refresh/style/index'
import 'vant/es/radio/style/index'
import 'vant/es/radio-group/style/index'
import 'vant/es/search/style/index'
import 'vant/es/skeleton/style/index'
import 'vant/es/stepper/style/index'
import 'vant/es/switch/style/index'
import 'vant/es/tab/style/index'
import 'vant/es/tabbar/style/index'
import 'vant/es/tabbar-item/style/index'
import 'vant/es/tabs/style/index'
import 'vant/es/tag/style/index'
import 'vant/es/time-picker/style/index'
import 'vant/es/toast/style/index'

const components = [
  Button,
  Calendar,
  Cell,
  CellGroup,
  Checkbox,
  Empty,
  Field,
  Form,
  Grid,
  GridItem,
  Icon,
  List,
  Loading,
  NavBar,
  NoticeBar,
  Picker,
  Popup,
  Progress,
  PullRefresh,
  Radio,
  RadioGroup,
  Search,
  Skeleton,
  Stepper,
  Switch,
  Tab,
  Tabbar,
  TabbarItem,
  Tabs,
  Tag,
  TimePicker
]

/** 逐个 app.use：Vant 组件自带 install，注册粒度即引入粒度 */
export function setupVant(app) {
  components.forEach((component) => app.use(component))
}
