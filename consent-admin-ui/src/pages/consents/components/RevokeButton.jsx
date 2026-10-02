import { REVOKABLE } from "../constants.js";

export default function RevokeButton({ item, busy, onRevoke }) {
  const enabled = REVOKABLE.has(item.status) && !busy;
  const hint = enabled
    ? "Thu hồi consent"
    : "Chỉ thu hồi khi trạng thái là Đã đồng ý hoặc Tạm dừng";
  return (
    <button
      className="btn danger compact"
      type="button"
      disabled={!enabled}
      title={hint}
      onClick={(event) => {
        event.stopPropagation();
        onRevoke(item);
      }}
    >
      Revoke
    </button>
  );
}
