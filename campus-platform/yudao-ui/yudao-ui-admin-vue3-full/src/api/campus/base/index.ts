import request from '@/config/axios'

export const getCampusPage = (resource: string, params: PageParam) => {
  return request.get({ url: `/campus/${resource}/page`, params })
}

export const getCampus = (resource: string, id: number) => {
  return request.get({ url: `/campus/${resource}/get?id=${id}` })
}

export const createCampus = (resource: string, data: Record<string, any>) => {
  return request.post({ url: `/campus/${resource}/create`, data })
}

export const updateCampus = (resource: string, data: Record<string, any>) => {
  return request.put({ url: `/campus/${resource}/update`, data })
}

export const deleteCampus = (resource: string, id: number) => {
  return request.delete({ url: `/campus/${resource}/delete?id=${id}` })
}
