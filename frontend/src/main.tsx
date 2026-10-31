import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { AdminHome, AdminLayout } from './admin/AdminLayout'
import { AdminLocationPage } from './admin/AdminLocationPage'
import { NewLocationPage } from './admin/NewLocationPage'
import { EditLocationPage } from './admin/EditLocationPage'
import { PricingPage } from './admin/PricingPage'
import { NotFound } from './components/NotFound'
import { DriverLayout } from './driver/DriverLayout'
import { HistoryPage } from './driver/HistoryPage'
import { LocationPage } from './driver/LocationPage'
import { LocationsPage } from './driver/LocationsPage'
import { MyParkingPage } from './driver/MyParkingPage'
import { NotificationsPage } from './driver/NotificationsPage'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
})

const router = createBrowserRouter([
  {
    path: '/',
    element: <DriverLayout />,
    children: [
      { index: true, element: <LocationsPage /> },
      { path: 'locations/:id', element: <LocationPage /> },
      { path: 'parking', element: <MyParkingPage /> },
      { path: 'history', element: <HistoryPage /> },
      { path: 'notifications', element: <NotificationsPage /> },
    ],
  },
  {
    path: '/admin',
    element: <AdminLayout />,
    children: [
      { index: true, element: <AdminHome /> },
      { path: 'locations/new', element: <NewLocationPage /> },
      { path: 'locations/:id', element: <AdminLocationPage /> },
      { path: 'locations/:id/pricing', element: <PricingPage /> },
      { path: 'locations/:id/edit', element: <EditLocationPage /> },
    ],
  },
  { path: '*', element: <NotFound /> },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
