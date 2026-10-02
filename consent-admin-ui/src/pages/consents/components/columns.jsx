import RevokeButton from "./RevokeButton.jsx";
import { formatWhen, statusLabel } from "../format.js";

export function consentColumns({ busy, onRevoke }) {
  return [
    {
      key: "subject",
      title: "Chủ thể",
      render: (item) => (
        <>
          <b>{item.subjectId || "Chưa gắn"}</b>
          <div className="mono">{item.id}</div>
        </>
      )
    },
    {
      key: "type",
      title: "Loại",
      render: (item) => item.consentType
    },
    {
      key: "client",
      title: "Ứng dụng",
      render: (item) => item.clientId
    },
    {
      key: "purpose",
      title: "Mục đích",
      render: (item) => item.purpose || "—"
    },
    {
      key: "status",
      title: "Trạng thái",
      render: (item) => <span className={`pill ${item.status}`}>{statusLabel(item.status)}</span>
    },
    {
      key: "validUntil",
      title: "Hiệu lực đến",
      render: (item) => formatWhen(item.validUntil)
    },
    {
      key: "actions",
      title: "",
      className: "actions",
      render: (item) => <RevokeButton item={item} busy={busy} onRevoke={onRevoke} />
    }
  ];
}

export function detailFields(item) {
  return [
    ["Mã consent", item.id],
    ["Trạng thái", statusLabel(item.status)],
    ["Chủ thể", item.subjectId || "Chưa gắn"],
    ["Ứng dụng", item.clientId],
    ["Hệ thống nguồn", item.sourceSystem],
    ["Kênh", item.acquisitionChannel],
    ["Cách ghi nhận", item.captureMethod],
    ["Chính sách bằng chứng", item.evidencePolicy],
    ["Trạng thái bằng chứng", item.evidenceStatus],
    ["Hiệu lực từ", formatWhen(item.validFrom)],
    ["Hiệu lực đến", formatWhen(item.validUntil)],
    ["Cập nhật", formatWhen(item.updatedAt)],
    ["Mã yêu cầu ngoài", item.externalRequestId || "—"],
    ["Tham chiếu ủy quyền", item.authorizationReference || "—"],
    ["Người tạo", item.createdBy]
  ];
}
