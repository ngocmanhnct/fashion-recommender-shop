import NamedResourceManager from '../../components/NamedResourceManager';
import { categoryAdminApi } from '../../services/adminCatalogApi';
import { getCategories } from '../../services/catalogApi';

function CategoryAdminPage() {
  return (
    <NamedResourceManager
      title="Danh mục"
      noun="danh mục"
      loadAll={getCategories}
      api={categoryAdminApi}
    />
  );
}

export default CategoryAdminPage;
