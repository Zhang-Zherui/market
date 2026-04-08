import request from '../utils/request'

// 用户模块
export const sendCode = (email) => request.post('/user/code', null, { params: { email } })

export const register = (data) => request.post('/user/register', data)

export const login = (data) => request.post('/user/login', data)

export const logout = () => request.post('/user/logout')

export const getMe = () => request.get('/user/me')

export const updateNickName = (nickName) => request.put('/user', { nickName })

export const changePassword = (data) => request.post('/user/password', data)

// 优惠券模块
export const getVoucherList = (params) => request.get('/voucher/list', { params })

export const getVoucherDetail = (id) => request.get(`/voucher/${id}`)

export const addVoucher = (data) => request.post('/voucher', data)

export const addSeckillVoucher = (data) => request.post('/voucher/seckill', data)

export const updateVoucher = (data) => request.put('/voucher', data)

export const deleteVoucher = (id) => request.delete(`/voucher/${id}`)

export const updateVoucherStatus = (id, status) =>
  request.put(`/voucher/${id}/status`, null, { params: { status } })

export const updateStock = (id, stock) =>
  request.put(`/voucher/stock/${id}`, null, { params: { stock } })

// 订单模块
export const buyVoucher = (id) => request.post(`/voucher-order/${id}`)

export const seckillVoucher = (id) => request.post(`/voucher-order/seckill/${id}`)

export const getMyOrders = () => request.get('/voucher-order/my')

export const getOrderDetail = (id) => request.get(`/voucher-order/${id}`)

export const payOrder = (id, payType) =>
  request.put(`/voucher-order/${id}/pay`, null, { params: { payType } })

export const cancelOrder = (id) => request.put(`/voucher-order/${id}/cancel`)

export const useRefund = (id) => request.put(`/voucher-order/${id}/refund`)

export const confirmRefund = (id) => request.put(`/voucher-order/${id}/refund/confirm`)
