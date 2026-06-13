import { computed, ref } from 'vue'
import request from './request'

export const normalizeRole = (role) => {
  if (!role) return ''
  const normalized = String(role).trim().toUpperCase()
  return normalized === 'ADMIN' ? 'SUPER_ADMIN' : normalized
}

export const getAdminUser = () => {
  try {
    return JSON.parse(localStorage.getItem('admin_user') || '{}')
  } catch (e) {
    return {}
  }
}

export const isSuperAdminUser = (user) => normalizeRole(user?.role) === 'SUPER_ADMIN'

export const isChannelAdminUser = (user) => normalizeRole(user?.role) === 'CHANNEL'

export const isChannelDisabled = (channel) => {
  return String(channel?.status || '').toUpperCase() === 'DISABLED'
}

export const formatChannelLabel = (channel) => {
  if (!channel) return '-'
  const name = channel.name || channel.channelName || channel.code || channel.channelCode || '-'
  const code = channel.code || channel.channelCode
  return code && code !== name ? `${name}（${code}）` : name
}

export const resolveChannelText = (row, channels = []) => {
  if (row?.channelName || row?.channelCode) {
    return formatChannelLabel({ name: row.channelName, code: row.channelCode })
  }
  const channelId = row?.channelId
  if (channelId !== null && channelId !== undefined) {
    const channel = channels.find(item => Number(item.id) === Number(channelId))
    if (channel) return formatChannelLabel(channel)
  }
  if (row?.apkChannel) {
    return row.apkChannel
  }
  if (channelId !== null && channelId !== undefined) {
    return `#${channelId}`
  }
  return 'main'
}

export const channelFilterParam = (isSuperAdmin, channelId) => {
  if (!isSuperAdmin) return undefined
  return channelId || undefined
}

export const formatDateTime = (value) => {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

export const useAdminSession = () => {
  const userInfo = ref(getAdminUser())
  const channels = ref([])
  const channelLoading = ref(false)

  const role = computed(() => normalizeRole(userInfo.value?.role))
  const isSuperAdmin = computed(() => role.value === 'SUPER_ADMIN')
  const isChannelAdmin = computed(() => role.value === 'CHANNEL')
  const ownChannelId = computed(() => userInfo.value?.channelId || null)
  const ownChannel = computed(() => {
    if (!ownChannelId.value) return null
    return channels.value.find(item => Number(item.id) === Number(ownChannelId.value)) || null
  })
  const isReadonlyChannel = computed(() => {
    if (!isChannelAdmin.value) return false
    if (channelLoading.value) return true
    if (!ownChannel.value) return true
    return isChannelDisabled(ownChannel.value)
  })

  const reloadUserInfo = () => {
    userInfo.value = getAdminUser()
  }

  const fetchChannels = async () => {
    if (!isSuperAdmin.value && !isChannelAdmin.value) {
      channels.value = []
      return []
    }
    channelLoading.value = true
    try {
      if (isSuperAdmin.value) {
        channels.value = await request.get('/channels')
      } else if (ownChannelId.value) {
        const channel = await request.get(`/channels/${ownChannelId.value}`)
        channels.value = channel ? [channel] : []
      }
      return channels.value
    } finally {
      channelLoading.value = false
    }
  }

  const channelText = (row) => resolveChannelText(row, channels.value)

  return {
    userInfo,
    role,
    isSuperAdmin,
    isChannelAdmin,
    ownChannelId,
    ownChannel,
    isReadonlyChannel,
    channels,
    channelLoading,
    reloadUserInfo,
    fetchChannels,
    channelText
  }
}
