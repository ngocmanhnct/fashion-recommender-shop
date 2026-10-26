"""Độ tương đồng "hay được mua cùng" giữa 2 sản phẩm (lọc cộng tác theo sản phẩm, item-based).

Dựng ma trận khách × sản phẩm (1 nếu khách đã mua sản phẩm trên đơn khác CANCELLED), rồi đo
cosine giữa 2 cột sản phẩm:

    sim(i, j) = c(i, j) / sqrt(n(i) · n(j)) · c(i, j) / (c(i, j) + SHRINKAGE)

c(i, j) là số khách mua cả 2 sản phẩm, n(i) là số khách mua sản phẩm i. Thừa số cuối kéo các cặp
chỉ có 1–2 khách chung về gần 0, để 1 khách mua ngẫu nhiên 2 món không thành "hay mua cùng".
Mỗi sản phẩm chỉ giữ TOP_NEIGHBOURS sản phẩm giống nhất cho ma trận nhỏ.
"""

from collections.abc import Iterable

import numpy as np
from scipy import sparse

SHRINKAGE = 3.0
TOP_NEIGHBOURS = 50


def build_copurchase(
    pairs: Iterable[tuple[int, int]], index: dict[int, int], size: int
) -> sparse.csr_matrix:
    """Ma trận sản phẩm × sản phẩm (đường chéo 0) theo thứ tự hàng `index` của vector sản phẩm.

    Bỏ qua sản phẩm không có trong `index` (đã xóa mềm).
    """
    accounts: dict[int, int] = {}
    rows, cols = [], []
    for account_id, product_id in pairs:
        col = index.get(product_id)
        if col is None:
            continue
        rows.append(accounts.setdefault(account_id, len(accounts)))
        cols.append(col)
    if not rows:
        return sparse.csr_matrix((size, size))
    bought = sparse.csr_matrix((np.ones(len(rows)), (rows, cols)), shape=(len(accounts), size))
    bought.data[:] = 1.0  # cặp trùng (nếu có) vẫn chỉ tính 1
    together = (bought.T @ bought).tocsr()
    together.setdiag(0)
    together.eliminate_zeros()
    counts = np.asarray(bought.sum(axis=0)).ravel()

    coo = together.tocoo()
    co = coo.data
    similarity = co / np.sqrt(counts[coo.row] * counts[coo.col]) * co / (co + SHRINKAGE)
    matrix = sparse.csr_matrix((similarity, (coo.row, coo.col)), shape=(size, size))
    return _keep_top(matrix, TOP_NEIGHBOURS)


def _keep_top(matrix: sparse.csr_matrix, top: int) -> sparse.csr_matrix:
    """Mỗi hàng chỉ giữ `top` giá trị lớn nhất."""
    rows, cols, values = [], [], []
    for row in range(matrix.shape[0]):
        start, end = matrix.indptr[row], matrix.indptr[row + 1]
        if start == end:
            continue
        data = matrix.data[start:end]
        keep = np.argsort(-data)[:top]
        rows.extend([row] * len(keep))
        cols.extend(matrix.indices[start:end][keep])
        values.extend(data[keep])
    return sparse.csr_matrix((values, (rows, cols)), shape=matrix.shape)
