import { useEffect } from "react";
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { Button, Form, Input, Select } from "antd";
import { useSearchParams } from "react-router-dom";
import { revokeConsent, searchConsents } from "../../api/consents.js";
import { useDebouncedSearch } from "../../hooks/useDebouncedSearch.js";
import ConsentDetail from "./components/ConsentDetail.jsx";
import ConsentTable from "./components/ConsentTable.jsx";
import {
  CONSENTS_QUERY_KEY,
  PAGE_SIZE,
  REVOKABLE,
  STATUS_OPTIONS,
} from "./constants.js";

export default function ConsentPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const status = searchParams.get("status") || "";
  const subjectId = searchParams.get("subjectId") || "";
  const page = Number(searchParams.get("page") || 0);
  const selectedId = searchParams.get("selected");
  const { keyword, setKeyword, debouncedKeyword } =
    useDebouncedSearch(subjectId);

  const listQuery = useQuery({
    queryKey: [
      CONSENTS_QUERY_KEY,
      { status, subjectId, page, size: PAGE_SIZE },
    ],
    queryFn: () => searchConsents({ status, subjectId, page, size: PAGE_SIZE }),
    placeholderData: keepPreviousData,
  });

  const revokeMutation = useMutation({
    mutationFn: revokeConsent,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: [CONSENTS_QUERY_KEY] }),
  });

  const result = listQuery.data;
  const items = result?.items || [];
  const selected = items.find((item) => item.id === selectedId) || null;
  const busy = listQuery.isFetching || revokeMutation.isPending;

  function replaceParams(patch) {
    const next = new URLSearchParams(searchParams);
    Object.entries(patch).forEach(([key, value]) => {
      if (value === "" || value == null) next.delete(key);
      else next.set(key, String(value));
    });
    setSearchParams(next, { replace: true });
  }

  useEffect(() => {
    const next = debouncedKeyword.trim();
    if (next === subjectId) return;
    const params = new URLSearchParams(searchParams);
    if (next) params.set("subjectId", next);
    else params.delete("subjectId");
    params.delete("page");
    params.delete("selected");
    setSearchParams(params, { replace: true });
  }, [debouncedKeyword, searchParams, setSearchParams, subjectId]);

  function applyFilter(values) {
    replaceParams({
      status: values.status || "",
      subjectId: keyword.trim(),
      page: null,
      selected: null,
    });
  }

  async function revoke(item) {
    if (!REVOKABLE.has(item.status) || revokeMutation.isPending) return;
    const confirmed = window.confirm(
      `Thu hồi consent của ${item.subjectId || item.id}?`,
    );
    if (!confirmed) return;
    replaceParams({ selected: item.id });
    revokeMutation.reset();
    try {
      await revokeMutation.mutateAsync(item.id);
    } catch {
      // Lỗi hiển thị từ revokeMutation.error.
    }
  }

  return (
    <main className="page">
      <div className="page-head">
        <div>
          <h1>Danh sách consent</h1>
          <p>
            {result
              ? `${result.totalElements} bản ghi`
              : "Đang tải dữ liệu từ Consent Core"}
          </p>
        </div>
      </div>
      <Form
        className="filter-form"
        layout="inline"
        initialValues={{ status }}
        onFinish={applyFilter}
      >
        <Form.Item name="status">
          <Select options={STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item className="filter-subject">
          <Input
            allowClear
            value={keyword}
            placeholder="Lọc theo mã chủ thể, ví dụ psu-10001"
            onChange={(event) => setKeyword(event.target.value)}
          />
        </Form.Item>
        <Button type="primary" htmlType="submit">
          Lọc
        </Button>
        <Button onClick={() => listQuery.refetch()}>Tải lại</Button>
      </Form>
      {listQuery.isError ? (
        <div className="error">{listQuery.error.message}</div>
      ) : null}
      {revokeMutation.isError ? (
        <div className="error">{revokeMutation.error.message}</div>
      ) : null}
      <div className="table-wrap">
        {listQuery.isPending ? (
          <div className="loading">Đang tải danh sách…</div>
        ) : (
          <ConsentTable
            items={items}
            selectedId={selectedId}
            busy={busy}
            onSelect={(id) => replaceParams({ selected: id })}
            onRevoke={revoke}
          />
        )}
      </div>
      <Pager
        result={result}
        onPage={(nextPage) => replaceParams({ page: nextPage || null })}
      />
      {selected ? (
        <ConsentDetail item={selected} busy={busy} onRevoke={revoke} />
      ) : null}
    </main>
  );
}

function Pager({ result, onPage }) {
  if (!result || result.totalPages <= 1) return null;
  const page = result.page + 1;
  return (
    <div className="pager">
      <span>
        Trang {page} / {result.totalPages}
      </span>
      <span>
        <button
          className="btn ghost"
          type="button"
          disabled={result.page <= 0}
          onClick={() => onPage(result.page - 1)}
        >
          Trước
        </button>
        <button
          className="btn ghost"
          type="button"
          disabled={page >= result.totalPages}
          onClick={() => onPage(result.page + 1)}
        >
          Sau
        </button>
      </span>
    </div>
  );
}
