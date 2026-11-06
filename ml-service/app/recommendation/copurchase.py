"""Độ tương đồng "hay được mua cùng" giữa 2 sản phẩm (lọc cộng tác theo sản phẩm, item-based).

Dựng ma trận khách × sản phẩm (1 nếu khách đã mua sản phẩm trên đơn khác CANCELLED), rồi đo
cosine giữa 2 cột sản phẩm:

    sim(i, j) = c(i, j) / sqrt(n(i) · n(j)) · c(i, j) / (c(i, j) + SHRINKAGE)

c(i, j) là số khách mua cả 2 sản phẩm, n(i) là số khách mua sản phẩm i. Thừa số cuối kéo các cặp
chỉ có 1–2 khách chung về gần 0, để 1 khách mua ngẫu nhiên 2 món không thành "hay mua cùng".
Mỗi sản phẩm chỉ giữ TOP_NEIGHBOURS sản phẩm giống nhất cho ma trận nhỏ (bằng nhau thì sản phẩm
đứng trước trong danh mục được giữ).

c(i, j) tính theo từng khối BLOCK_PRODUCTS sản phẩm và chỉ giữ phần đã lọc, nên không bao giờ
phải giữ cả ma trận sản phẩm × sản phẩm: với dữ liệu H&M (hàng chục nghìn sản phẩm, hàng trăm
nghìn khách) ma trận đó chiếm vài GB.
"""

from array import array
from collections.abc import Iterable

import numpy as np
from scipy import sparse

SHRINKAGE = 3.0
TOP_NEIGHBOURS = 50
BLOCK_PRODUCTS = 512


def build_copurchase(
    pairs: Iterable[tuple[int, int]], index: dict[int, int], size: int
) -> sparse.csr_matrix:
    """Ma trận sản phẩm × sản phẩm (đường chéo 0) theo thứ tự hàng `index` của vector sản phẩm.

    Bỏ qua sản phẩm không có trong `index` (đã xóa mềm).
    """
    accounts: dict[int, int] = {}
    rows, cols = array("i"), array("i")
    for account_id, product_id in pairs:
        col = index.get(product_id)
        if col is None:
            continue
        rows.append(accounts.setdefault(account_id, len(accounts)))
        cols.append(col)
    if not rows:
        return sparse.csr_matrix((size, size))
    bought = sparse.csr_matrix(
        (np.ones(len(rows)), (np.frombuffer(rows, dtype=np.int32), np.frombuffer(cols, np.int32))),
        shape=(len(accounts), size),
    )
    bought.data[:] = 1.0  # cặp trùng (nếu có) vẫn chỉ tính 1
    counts = np.asarray(bought.sum(axis=0)).ravel()
    by_product = bought.T.tocsr()  # sản phẩm × khách
    kept_rows, kept_cols, kept_values = [], [], []
    for start in range(0, size, BLOCK_PRODUCTS):
        together = (by_product[start : start + BLOCK_PRODUCTS] @ bought).tocoo()
        row = together.row.astype(np.int64) + start
        off_diagonal = row != together.col
        row, col, co = row[off_diagonal], together.col[off_diagonal], together.data[off_diagonal]
        similarity = co / np.sqrt(counts[row] * counts[col]) * co / (co + SHRINKAGE)
        keep = _top_per_row(row, col, similarity, TOP_NEIGHBOURS)
        kept_rows.append(row[keep])
        kept_cols.append(col[keep])
        kept_values.append(similarity[keep])
    return sparse.csr_matrix(
        (np.concatenate(kept_values), (np.concatenate(kept_rows), np.concatenate(kept_cols))),
        shape=(size, size),
    )


def _top_per_row(row: np.ndarray, col: np.ndarray, value: np.ndarray, top: int) -> np.ndarray:
    """Vị trí các phần tử được giữ: mỗi hàng `top` giá trị lớn nhất, bằng nhau thì cột nhỏ trước."""
    if len(row) == 0:
        return np.zeros(0, dtype=np.int64)
    order = np.lexsort((col, -value, row))
    sorted_rows = row[order]
    first = np.flatnonzero(np.r_[True, sorted_rows[1:] != sorted_rows[:-1]])
    rank = np.arange(len(order)) - np.repeat(first, np.diff(np.r_[first, len(order)]))
    return order[rank < top]
