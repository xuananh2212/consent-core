export const STATUS_LABEL = {
  REGISTERED: "Đã đăng ký",
  AWAITING_AUTHORIZATION: "Chờ xác nhận",
  AUTHORIZED: "Đã đồng ý",
  REJECTED: "Từ chối",
  SUSPENDED: "Tạm dừng",
  REVOKED: "Đã thu hồi",
  EXPIRED: "Hết hạn",
  CANCELLED: "Đã hủy"
};

export const STATUS_OPTIONS = [
  { value: "", label: "Mọi trạng thái" },
  ...Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))
];

export const REVOKABLE = new Set(["AUTHORIZED", "SUSPENDED"]);
export const PAGE_SIZE = 8;
export const CONSENTS_QUERY_KEY = "consents";
