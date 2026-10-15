import NamedResourceManager from '../../components/NamedResourceManager';
import { brandAdminApi } from '../../services/adminCatalogApi';
import { getBrands } from '../../services/catalogApi';

function BrandAdminPage() {
  return (
    <NamedResourceManager
      title="Thương hiệu"
      noun="thương hiệu"
      loadAll={getBrands}
      api={brandAdminApi}
    />
  );
}

export default BrandAdminPage;
