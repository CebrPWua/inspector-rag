import { Outlet, NavLink, useLocation } from 'react-router-dom'
import { Layout, Menu, Typography, ConfigProvider } from 'antd'
import {
  SearchOutlined, FileTextOutlined, ToolOutlined,
  HistoryOutlined, BarChartOutlined, SettingOutlined,
} from '@ant-design/icons'
import './index.css'

const { Header, Content } = Layout
const { Title } = Typography

const NAV_ITEMS = [
  { key: '/', label: '问答检索', icon: <SearchOutlined /> },
  { key: '/files', label: '法规管理', icon: <FileTextOutlined /> },
  { key: '/tasks/dead-letter', label: '任务运维', icon: <ToolOutlined /> },
  { key: '/records', label: '审计记录', icon: <HistoryOutlined /> },
  { key: '/reports', label: '质量报表', icon: <BarChartOutlined /> },
  { key: '/qa-config', label: '问答配置', icon: <SettingOutlined /> },
]

export default function App() {
  const location = useLocation()

  // 根据当前路径匹配激活菜单项
  const selectedKey = NAV_ITEMS
    .slice()
    .reverse()
    .find((item) => location.pathname === item.key || location.pathname.startsWith(item.key + '/'))
    ?.key ?? '/'

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#1677ff',
          borderRadius: 8,
          fontFamily: "-apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif",
        },
      }}
    >
      <Layout style={{ minHeight: '100vh' }}>
        <Header className="app-header">
          <div className="header-brand">
            <SearchOutlined style={{ fontSize: 20, color: '#1677ff' }} />
            <Title level={5} style={{ margin: 0, color: '#fff', letterSpacing: 1 }}>
              Inspector RAG
            </Title>
          </div>
          <Menu
            theme="dark"
            mode="horizontal"
            selectedKeys={[selectedKey]}
            items={NAV_ITEMS.map((item) => ({
              key: item.key,
              icon: item.icon,
              label: <NavLink to={item.key}>{item.label}</NavLink>,
            }))}
            style={{ flex: 1, minWidth: 0, border: 'none', background: 'transparent' }}
          />
        </Header>
        <Content style={{ background: '#f5f5f5', minHeight: 'calc(100vh - 64px)' }}>
          <Outlet />
        </Content>
      </Layout>
    </ConfigProvider>
  )
}
