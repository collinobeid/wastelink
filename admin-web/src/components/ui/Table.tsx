import React from 'react';

export interface Column<T> {
  key: string;
  header: string;
  render?: (item: T) => React.ReactNode;
  align?: 'left' | 'center' | 'right';
  width?: string;
}

export interface TableProps<T> {
  columns: Column<T>[];
  data: T[];
  keyExtractor: (item: T) => string;
  emptyMessage?: string;
  isLoading?: boolean;
}

export function Table<T>({
  columns,
  data,
  keyExtractor,
  emptyMessage = 'No records found.',
  isLoading = false,
}: TableProps<T>) {
  if (isLoading) {
    return (
      <div className="w-full py-12 flex flex-col items-center justify-center space-y-3">
        <div className="w-8 h-8 rounded-full border-2 border-emerald-500 border-t-transparent animate-spin" />
        <p className="text-xs text-slate-400">Loading data...</p>
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <div className="w-full py-12 text-center text-slate-400 text-sm">
        <p>{emptyMessage}</p>
      </div>
    );
  }

  return (
    <div className="w-full overflow-x-auto">
      <table className="w-full text-left text-sm border-collapse">
        <thead>
          <tr className="border-b border-slate-700/60 bg-slate-850/60 text-xs font-semibold text-slate-400 uppercase tracking-wider">
            {columns.map((col) => (
              <th
                key={col.key}
                scope="col"
                className={`px-4 py-3 ${col.width || ''} ${
                  col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left'
                }`}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/80">
          {data.map((item) => (
            <tr key={keyExtractor(item)} className="hover:bg-slate-800/40 transition-colors">
              {columns.map((col) => {
                const content = col.render
                  ? col.render(item)
                  : (item as Record<string, unknown>)[col.key] !== undefined
                  ? String((item as Record<string, unknown>)[col.key])
                  : null;

                return (
                  <td
                    key={col.key}
                    className={`px-4 py-3 text-slate-200 ${
                      col.align === 'right' ? 'text-right' : col.align === 'center' ? 'text-center' : 'text-left'
                    }`}
                  >
                    {content}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
