import { CertificateManagementPage } from '@/pages/certificates/certificate-management-page'
import { CertificatesPage } from '@/pages/certificates/certificates-page'
import { useAuthStore } from '@/stores/auth-store'

/** A student sees their own requests and certificates; an administrator manages everyone's. */
export function CertificatesIndexPage() {
  const role = useAuthStore((state) => state.user?.role)
  return role === 'STUDENT' ? <CertificatesPage /> : <CertificateManagementPage />
}
