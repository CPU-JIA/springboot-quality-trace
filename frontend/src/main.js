import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElAlert } from 'element-plus/es/components/alert/index'
import { ElButton } from 'element-plus/es/components/button/index'
import { ElCheckbox, ElCheckboxGroup } from 'element-plus/es/components/checkbox/index'
import { ElConfigProvider } from 'element-plus/es/components/config-provider/index'
import { ElContainer, ElHeader, ElMain } from 'element-plus/es/components/container/index'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index'
import { ElDescriptions, ElDescriptionsItem } from 'element-plus/es/components/descriptions/index'
import { ElDialog } from 'element-plus/es/components/dialog/index'
import { ElDrawer } from 'element-plus/es/components/drawer/index'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index'
import { ElIcon } from 'element-plus/es/components/icon/index'
import { ElInput } from 'element-plus/es/components/input/index'
import { ElInputNumber } from 'element-plus/es/components/input-number/index'
import { ElLoading } from 'element-plus/es/components/loading/index'
import { ElMenu, ElMenuItem } from 'element-plus/es/components/menu/index'
import { ElPagination } from 'element-plus/es/components/pagination/index'
import { ElRadio, ElRadioButton, ElRadioGroup } from 'element-plus/es/components/radio/index'
import { ElSegmented } from 'element-plus/es/components/segmented/index'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index'
import { ElSwitch } from 'element-plus/es/components/switch/index'
import { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index'
import { ElTag } from 'element-plus/es/components/tag/index'
import { ElTooltip } from 'element-plus/es/components/tooltip/index'
import { ElTree } from 'element-plus/es/components/tree/index'
import 'element-plus/dist/index.css'
import {
  Bell,
  Box,
  DataBoard,
  Finished,
  Operation,
  SetUp,
  Share,
  TrendCharts,
  User,
  Van,
  Warning
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import './assets/styles.css'

const app = createApp(App)

const elementComponents = [
  ElAlert,
  ElButton,
  ElCheckbox,
  ElCheckboxGroup,
  ElConfigProvider,
  ElContainer,
  ElDatePicker,
  ElDescriptions,
  ElDescriptionsItem,
  ElDialog,
  ElDrawer,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElForm,
  ElFormItem,
  ElHeader,
  ElIcon,
  ElInput,
  ElInputNumber,
  ElMain,
  ElMenu,
  ElMenuItem,
  ElOption,
  ElPagination,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElSegmented,
  ElSelect,
  ElSwitch,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
  ElTag,
  ElTooltip,
  ElTree
]

const menuIcons = {
  Bell,
  Box,
  DataBoard,
  Finished,
  Operation,
  SetUp,
  Share,
  TrendCharts,
  User,
  Van,
  Warning
}

for (const component of elementComponents) {
  app.use(component)
}

for (const [key, component] of Object.entries(menuIcons)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElLoading)
app.mount('#app')
