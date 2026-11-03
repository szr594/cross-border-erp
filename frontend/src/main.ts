import { createApp } from 'vue'
import { ElAlert } from 'element-plus/es/components/alert/index.mjs'
import { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElCheckbox } from 'element-plus/es/components/checkbox/index.mjs'
import { ElAside, ElContainer, ElHeader, ElMain } from 'element-plus/es/components/container/index.mjs'
import { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
import { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
import { ElForm, ElFormItem } from 'element-plus/es/components/form/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
import { ElLoading } from 'element-plus/es/components/loading/index.mjs'
import { ElMenu, ElMenuItem } from 'element-plus/es/components/menu/index.mjs'
import { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
import { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
import { ElProgress } from 'element-plus/es/components/progress/index.mjs'
import { ElResult } from 'element-plus/es/components/result/index.mjs'
import { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
import { ElTag } from 'element-plus/es/components/tag/index.mjs'
import { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index.mjs'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/tokens.css'
import './styles/main.css'

const app = createApp(App)
const components = [
  ElAlert, ElAside, ElAvatar, ElButton, ElCheckbox, ElContainer, ElDatePicker,
  ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElEmpty, ElForm,
  ElFormItem, ElHeader, ElIcon, ElInput, ElInputNumber, ElMain, ElMenu,
  ElMenuItem, ElOption, ElPagination, ElProgress, ElResult, ElSelect, ElTable,
  ElTableColumn, ElTag, ElTimeline, ElTimelineItem,
]
for (const component of components) {
  if (component.name) {
    app.component(component.name, component)
  }
}
app.use(ElLoading).use(router).mount('#app')
