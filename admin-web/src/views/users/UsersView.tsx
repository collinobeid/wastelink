import React, { useEffect, useState, useCallback } from 'react';
import { Card, CardHeader, CardContent } from '../../components/ui/Card';
import { Table, Column } from '../../components/ui/Table';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { Input } from '../../components/ui/Input';
import { AlertBanner } from '../../components/feedback/AlertBanner';
import { fetchUsers, parseFirestoreError } from '../../services/dataService';
import { UserProfile, UserRole, AccountStatus } from '../../domain/types';
import { Search, Eye, RefreshCw, UserCheck } from 'lucide-react';

export const UsersView: React.FC = () => {
  const [users, setUsers] = useState<UserProfile[]>([]);
  const [roleFilter, setRoleFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedUser, setSelectedUser] = useState<UserProfile | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [queryError, setQueryError] = useState<string | null>(null);

  const loadUsers = useCallback(async () => {
    setIsLoading(true);
    setQueryError(null);
    try {
      const data = await fetchUsers(roleFilter === 'ALL' ? undefined : roleFilter);
      setUsers(data);
    } catch (err: unknown) {
      const parsed = parseFirestoreError(err, 'Failed to fetch user profiles.');
      setQueryError(parsed.message);
    } finally {
      setIsLoading(false);
    }
  }, [roleFilter]);

  useEffect(() => {
    loadUsers();
  }, [loadUsers]);

  const filteredUsers = users.filter((u) => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    return (
      u.fullName.toLowerCase().includes(query) ||
      u.email.toLowerCase().includes(query) ||
      u.phoneNumber.toLowerCase().includes(query) ||
      u.id.toLowerCase().includes(query)
    );
  });

  const getRoleBadge = (role: UserRole) => {
    switch (role) {
      case 'admin':
        return <Badge variant="purple">ADMIN</Badge>;
      case 'collector':
        return <Badge variant="blue">COLLECTOR</Badge>;
      default:
        return <Badge variant="slate">RESIDENT</Badge>;
    }
  };

  const getStatusBadge = (status: AccountStatus | string) => {
    switch (status) {
      case 'ACTIVE':
        return <Badge variant="emerald">ACTIVE</Badge>;
      case 'RESTRICTED':
        return <Badge variant="amber">RESTRICTED</Badge>;
      case 'SUSPENDED':
        return <Badge variant="rose">SUSPENDED</Badge>;
      case 'BANNED':
        return <Badge variant="rose">BANNED</Badge>;
      default:
        return <Badge variant="slate">{status}</Badge>;
    }
  };

  const columns: Column<UserProfile>[] = [
    {
      key: 'fullName',
      header: 'User / Contact',
      render: (u) => (
        <div>
          <p className="font-semibold text-slate-100">{u.fullName || u.name || 'Anonymous'}</p>
          <p className="text-slate-400 text-xs">{u.email}</p>
        </div>
      ),
    },
    {
      key: 'role',
      header: 'Role',
      render: (u) => getRoleBadge(u.role),
    },
    {
      key: 'phoneNumber',
      header: 'Phone (E.164)',
      render: (u) => <span className="font-mono text-xs text-slate-300">{u.phoneNumber || u.phone || '—'}</span>,
    },
    {
      key: 'accountStatus',
      header: 'Standing',
      render: (u) => getStatusBadge(u.accountStatus || u.status || 'ACTIVE'),
    },
    {
      key: 'collectorVerificationStatus',
      header: 'Vetting Status',
      render: (u) =>
        u.role === 'collector' ? (
          <Badge
            variant={
              u.collectorVerificationStatus === 'APPROVED'
                ? 'emerald'
                : u.collectorVerificationStatus === 'REJECTED'
                ? 'rose'
                : 'amber'
            }
            size="sm"
          >
            {u.collectorVerificationStatus}
          </Badge>
        ) : (
          <span className="text-slate-500 text-xs">N/A</span>
        ),
    },
    {
      key: 'actions',
      header: 'Inspect',
      align: 'right',
      render: (u) => (
        <Button
          variant="ghost"
          size="sm"
          onClick={() => setSelectedUser(u)}
          leftIcon={<Eye className="w-3.5 h-3.5" />}
        >
          View
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight text-white">User Directory & Governance</h1>
          <p className="text-xs text-slate-400 mt-1">
            Authoritative registry of resident citizens, verified collectors, and municipal operators.
          </p>
        </div>
        <Button variant="secondary" size="sm" onClick={loadUsers} leftIcon={<RefreshCw className="w-4 h-4" />}>
          Refresh
        </Button>
      </div>

      {queryError && (
        <AlertBanner type="error" message={queryError} onClose={() => setQueryError(null)} />
      )}

      <Card variant="default">
        <CardHeader className="flex-col sm:flex-row gap-4 items-start sm:items-center justify-between">
          <div className="w-full sm:w-72">
            <Input
              placeholder="Search by name, email, phone..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              leftIcon={<Search className="w-4 h-4" />}
            />
          </div>

          <div className="flex items-center gap-2 overflow-x-auto w-full sm:w-auto">
            {['ALL', 'RESIDENT', 'COLLECTOR', 'ADMIN'].map((r) => (
              <button
                key={r}
                onClick={() => setRoleFilter(r)}
                className={`text-xs px-3 py-1.5 rounded-lg font-medium transition-colors ${
                  roleFilter === r
                    ? 'bg-emerald-600 text-white'
                    : 'bg-slate-800 text-slate-400 hover:text-white'
                }`}
              >
                {r}
              </button>
            ))}
          </div>
        </CardHeader>
        <CardContent className="p-0">
          <Table
            columns={columns}
            data={filteredUsers}
            keyExtractor={(u) => u.id}
            isLoading={isLoading}
            emptyMessage="No user profiles found in /users."
          />
        </CardContent>
      </Card>

      {/* User Inspection Modal */}
      {selectedUser && (
        <Modal
          isOpen={true}
          onClose={() => setSelectedUser(null)}
          title={selectedUser.fullName || 'User Profile'}
          description={`UID: ${selectedUser.id}`}
          footer={
            <Button variant="secondary" size="sm" onClick={() => setSelectedUser(null)}>
              Close
            </Button>
          }
        >
          <div className="space-y-4 text-xs">
            <div className="grid grid-cols-2 gap-3 p-3 bg-slate-950 rounded-lg border border-slate-800">
              <div>
                <span className="text-slate-500 font-semibold uppercase">Role</span>
                <p className="mt-1">{getRoleBadge(selectedUser.role)}</p>
              </div>
              <div>
                <span className="text-slate-500 font-semibold uppercase">Account Standing</span>
                <p className="mt-1">{getStatusBadge(selectedUser.accountStatus || selectedUser.status || 'ACTIVE')}</p>
              </div>
              <div>
                <span className="text-slate-500 font-semibold uppercase">Email</span>
                <p className="text-slate-200 mt-1">{selectedUser.email}</p>
              </div>
              <div>
                <span className="text-slate-500 font-semibold uppercase">Phone</span>
                <p className="text-slate-200 mt-1 font-mono">{selectedUser.phoneNumber || selectedUser.phone || '—'}</p>
              </div>
            </div>

            {selectedUser.role === 'collector' && (
              <div className="p-3 bg-slate-950 rounded-lg border border-slate-800 space-y-2">
                <span className="text-slate-400 font-semibold block flex items-center gap-1.5">
                  <UserCheck className="w-3.5 h-3.5 text-blue-400" />
                  Collector Fleet Credentials
                </span>
                <div className="grid grid-cols-2 gap-2 text-[11px]">
                  <div>
                    <span className="text-slate-500">Vehicle Type:</span>{' '}
                    <span className="text-slate-200">{selectedUser.vehicleType || 'Not registered'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Plate Number:</span>{' '}
                    <span className="text-slate-200 font-mono">{selectedUser.vehiclePlateNumber || 'Not registered'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Assigned Zone:</span>{' '}
                    <span className="text-slate-200">{selectedUser.defaultZoneId || 'Default Zone'}</span>
                  </div>
                  <div>
                    <span className="text-slate-500">Vetting Status:</span>{' '}
                    <span className="text-slate-200">{selectedUser.collectorVerificationStatus}</span>
                  </div>
                </div>
              </div>
            )}

            <div className="p-3 bg-blue-950/30 border border-blue-800/40 rounded-lg text-blue-300 text-[11px] leading-relaxed">
              <strong>Server-Side Moderation Boundary:</strong> Account status mutations require an atomic entry in <code>/user_moderation/{selectedUser.id}</code> and <code>/admin_actions_log</code>. Role changes cannot be executed by client SDKs.
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
};
