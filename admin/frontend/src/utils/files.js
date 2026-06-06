const missingThumbnailUrls = new Set()

export const normalizeFileUrl = (url) => {
  if (!url) return ''
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return url
}

export const thumbnailFileUrl = (url) => {
  if (!url) return ''
  const hashIndex = url.indexOf('#')
  const hash = hashIndex >= 0 ? url.slice(hashIndex) : ''
  const withoutHash = hashIndex >= 0 ? url.slice(0, hashIndex) : url
  const queryIndex = withoutHash.indexOf('?')
  const query = queryIndex >= 0 ? withoutHash.slice(queryIndex) : ''
  const path = queryIndex >= 0 ? withoutHash.slice(0, queryIndex) : withoutHash
  if (path.endsWith('.thumb.jpg')) {
    return url
  }
  const lastSlash = path.lastIndexOf('/')
  const lastDot = path.lastIndexOf('.')
  const thumbPath = lastDot > lastSlash
    ? `${path.slice(0, lastDot)}.thumb.jpg`
    : `${path}.thumb.jpg`
  return `${thumbPath}${query}${hash}`
}

export const fetchFileBlob = async (url) => {
  const normalizedUrl = normalizeFileUrl(url)
  const token = localStorage.getItem('admin_token')
  const response = await fetch(normalizedUrl, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!response.ok) {
    throw new Error(`文件下载失败: ${response.status}`)
  }
  return response.blob()
}

export const getObjectUrl = async (url, cache) => {
  if (cache?.has(url)) {
    return cache.get(url)
  }
  const blob = await fetchFileBlob(url)
  const objectUrl = URL.createObjectURL(blob)
  cache?.set(url, objectUrl)
  return objectUrl
}

export const getPreferredImageObjectUrl = async (url, cache) => {
  if (!url) return ''
  const thumbUrl = thumbnailFileUrl(url)
  if (thumbUrl && thumbUrl !== url && !missingThumbnailUrls.has(thumbUrl)) {
    try {
      return await getObjectUrl(thumbUrl, cache)
    } catch (e) {
      // Older uploads may not have thumbnails; fall back to the original.
      missingThumbnailUrls.add(thumbUrl)
    }
  }
  return getObjectUrl(url, cache)
}
