import React, { useState } from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { ErrorBoundary } from '../feedback/ErrorBoundary';

export const AdminShell: React.FC = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="min-h-screen bg-slate-950 flex text-slate-100">
      {/* Sidebar */}
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />

      {/* Main layout container */}
      <div className="flex-1 lg:pl-64 flex flex-col min-w-0">
        <Header onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />

        <main className="flex-1 p-6 lg:p-8 max-w-7xl w-full mx-auto">
          <ErrorBoundary>
            <Outlet />
          </ErrorBoundary>
        </main>
      </div>
    </div>
  );
};
