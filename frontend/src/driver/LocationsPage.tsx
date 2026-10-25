import { divIcon, latLngBounds } from 'leaflet'
import { useState } from 'react'
import { MapContainer, Marker, TileLayer } from 'react-leaflet'
import { Link, useNavigate } from 'react-router'
import { useLocations } from '../api/queries'
import type { ParkingLocation } from '../api/types'
import { OccupancyBar } from '../components/OccupancyBar'
import { QueryState } from '../components/QueryState'
import { describePricing } from '../lib/format'
import { button, field } from '../lib/ui'

export function LocationsPage() {
  const locations = useLocations()
  const [search, setSearch] = useState('')

  return (
    <>
      <div className="px-4 pt-2 pb-4">
        <h1 className="text-2xl font-extrabold tracking-tight">Find a spot</h1>
        <label className={`${field.label} mt-4`} htmlFor="search">
          Search car parks
        </label>
        <input
          id="search"
          type="search"
          className={field.input}
          placeholder="Name or street"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      <QueryState query={locations} loading="Loading car parks…">
        {(all) => {
          const term = search.trim().toLowerCase()
          const shown = all.filter((l) => `${l.name} ${l.address}`.toLowerCase().includes(term))
          return (
            <>
              <LocationsMap locations={shown} />
              {shown.length === 0 ? (
                <div className="px-4 py-8">
                  <p>No car parks match “{search}”. Check the spelling or clear the search.</p>
                  <button type="button" className={`${button.small} mt-3`} onClick={() => setSearch('')}>
                    Clear search
                  </button>
                </div>
              ) : (
                <ul className="divide-y divide-hairline border-y border-hairline">
                  {shown.map((location) => (
                    <LocationRow key={location.id} location={location} />
                  ))}
                </ul>
              )}
            </>
          )
        }}
      </QueryState>
      <p className="px-4 pt-8 text-sm text-ink-muted">
        Run a car park? <Link to="/admin" className="font-semibold text-sign hover:underline">Open the admin dashboard</Link>
      </p>
    </>
  )
}

function LocationRow({ location }: { location: ParkingLocation }) {
  const full = location.freeSpots === 0
  return (
    <li>
      <Link to={`/locations/${location.id}`} className="block px-4 py-4 hover:bg-surface">
        <span className="block text-lg font-semibold">{location.name}</span>
        <span className="block text-sm text-ink-muted">{location.address}</span>
        <span className="mt-2 flex items-center gap-3">
          <span className="tabular text-base font-bold">
            {full ? 'Full' : `${location.freeSpots} of ${location.totalSpots} free`}
          </span>
          <OccupancyBar free={location.freeSpots} total={location.totalSpots} />
        </span>
        <span className="mt-1 block text-sm">{describePricing(location.pricing)}</span>
      </Link>
    </li>
  )
}

/** A "P" plate per car park showing how many spots are free, like the signs at the entrance. */
function pin(free: number) {
  return divIcon({
    className: '',
    iconSize: [56, 32],
    iconAnchor: [28, 32],
    html: `<span class="tabular flex h-8 w-14 items-center justify-center gap-1 rounded-sign border-2 border-surface bg-sign text-sm font-extrabold whitespace-nowrap text-surface shadow-sheet">P ${free}</span>`,
  })
}

function LocationsMap({ locations }: { locations: ParkingLocation[] }) {
  const navigate = useNavigate()
  if (locations.length === 0) return null
  const bounds = latLngBounds(locations.map((l) => [l.latitude, l.longitude]))

  return (
    <div className="h-64 border-t border-hairline" aria-label="Map of car parks with free spot counts">
      <MapContainer bounds={bounds} boundsOptions={{ padding: [40, 40], maxZoom: 16 }} scrollWheelZoom={false} className="h-full">
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {locations.map((l) => (
          <Marker
            key={l.id}
            position={[l.latitude, l.longitude]}
            icon={pin(l.freeSpots)}
            title={`${l.name}, ${l.freeSpots} free`}
            eventHandlers={{ click: () => navigate(`/locations/${l.id}`) }}
          />
        ))}
      </MapContainer>
    </div>
  )
}
