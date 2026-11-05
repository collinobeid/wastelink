import React from 'react';
import { NavLink } from 'react-router-dom';
import {
  LayoutDashboard,
  Truck,
  Users,
  ShieldCheck,
  Trash2,
  MapPin,
  DollarSign,
  AlertCircle,
  Radio,
  BarChart3,
  History,
  Recycle,
} from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose?: () => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose }) => {
  const navItems = [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/requests', label: 'Waste Requests', icon: Truck },
    { to: '/users', label: 'User Directory', icon: Users },
    { to: '/vetting', label: 'Collector Vetting', icon: ShieldCheck },
    { to: '/categories', label: 'Waste Categories', icon: Trash2 },
    { to: '/zones', label: 'Service Zones', icon: MapPin },
    { to: '/pricing', label: 'Pricing Rules', icon: DollarSign },
    { to: '/disputes', label: 'Disputes & Grievances', icon: AlertCircle },
    { to: '/fleet', label: 'Fleet & Telemetry', icon: Radio },
    { to: '/reports', label: 'Reports & Exports', icon: BarChart3 },
    { to: '/audit', label: 'Administrative Audit', icon: History },
  ];

  return (
    <>
      {/* Mobile backdrop */}
      {isOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-950/70 backdrop-blur-sm lg:hidden"
          onClick={onClose}
        />
      )}

      <aside
        className={`fixed top-0 bottom-0 left-0 z-40 w-64 bg-slate-900 border-r border-slate-800 transition-transform duration-200 lg:translate-x-0 ${
          isOpen ? 'translate-x-0' : '-translate-x-full'
        } flex flex-col`}
      >
        {/* Brand header */}
        <div className="h-16 px-6 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/10 border border-emerald-500/30 flex items-center justify-center text-emerald-400">
              <Recycle className="w-5 h-5" />
            </div>
            <div>
              <span className="font-bold text-base tracking-tight text-white">WasteLink</span>
              <span className="text-[10px] uppercase font-bold tracking-widest text-emerald-400 block -mt-0.5">
                Admin Console
              </span>
            </div>
          </div>
        </div>

        {/* Navigation list */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            return (
              <NavLink
                key={item.to}
                to={item.to}
                onClick={onClose}
                className={({ isActive }) =>
                  `flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-emerald-950/60 text-emerald-300 border border-emerald-700/40 shadow-sm'
                      : 'text-slate-400 hover:text-slate-100 hover:bg-slate-800/60'
                  }`
                }
              >
                <Icon className="w-4 h-4 shrink-0" />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>

        {/* Footer info */}
        <div className="p-4 border-t border-slate-800/80 bg-slate-950/40">
          <p className="text-[11px] text-slate-500 font-medium">WasteLink v1.0.0-foundation</p>
          <p className="text-[10px] text-slate-600">Enterprise Operations</p>
        </div>
      </aside>
    </>
  );
};
