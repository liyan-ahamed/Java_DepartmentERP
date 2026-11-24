'use client';

import Link from 'next/link';
import { useState } from 'react';
import { usePathname } from 'next/navigation';
import { useAuth } from '@/contexts/auth-context';
import { buildNav, isActive, NavItem, NavSection, roleOf } from '@/lib/navigation';
import { AccountMenu } from './AccountMenu';
import { ChevronRight, GraduationCap, LogOut, Menu } from '@/components/ui/icons';

function NavLink({ item, pathname, onNavigate }: { item: NavItem; pathname: string; onNavigate: () => void }) {
  const active = isActive(pathname, item.href, item.exact);
  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={active ? 'page' : undefined}
      className={`nav-item ${active ? 'active' : ''}`}
    >
      <item.icon />
      <span className="truncate">{item.label}</span>
    </Link>
  );
}

interface SidebarProps {
  /** Closes the phone drawer after navigating; harmless on desktop. */
  onClose: () => void;
}

function NavDropdown({ section, pathname, onNavigate }: { section: NavSection; pathname: string; onNavigate: () => void }) {
  const active = section.items.some((item) => isActive(pathname, item.href, item.exact));
  const [open, setOpen] = useState(active);
  const Icon = section.icon;
  return (
    <div>
      <button type="button" className={`nav-item ${active ? 'active' : ''}`} onClick={() => setOpen((value) => !value)}
        aria-expanded={open} aria-controls="schedule-navigation">
        {Icon && <Icon />}
        <span>{section.label}</span>
        <ChevronRight className={`chev ${open ? 'open' : ''}`} />
      </button>
      <div id="schedule-navigation" className="nav-sub" hidden={!open}>
        {section.items.map((item) => <NavLink key={item.href} item={item} pathname={pathname} onNavigate={onNavigate} />)}
      </div>
    </div>
  );
}

/** Forma sidebar: brand, signed-in user card, grouped navigation, pinned bottom controls. */
export const Sidebar = ({ onClose }: SidebarProps) => {
  const { hasRole, logout } = useAuth();
  const pathname = usePathname();
  const role = roleOf(hasRole);
  const { main } = buildNav(role);

  return (
    <aside className="sidebar" aria-label="Main navigation">
      <div className="sidebar-brand">
        <span className="brand-mark">
          <GraduationCap />
        </span>
        Dept. ERP
        <button
          type="button"
          onClick={onClose}
          className="mobile-menu ml-auto w-[25px] h-[25px] p-1 items-center justify-center rounded-md text-muted hover:bg-soft"
          aria-label="Close navigation"
        >
          <Menu />
        </button>
      </div>

      {/* The signed-in user card opens the shared account menu (same menu as the top-bar avatar). */}
      <AccountMenu variant="sidebar" onNavigate={onClose} />

      <nav className="nav">
        {main.map((section) => section.dropdown ? (
          <NavDropdown key={`${section.label}:${section.items.some((item) => isActive(pathname, item.href))}`}
            section={section} pathname={pathname} onNavigate={onClose} />
        ) : (
          <div key={section.items[0].href}>
            {section.label && <div className="nav-label">{section.label}</div>}
            {section.items.map((item) => (
              <NavLink key={item.href} item={item} pathname={pathname} onNavigate={onClose} />
            ))}
          </div>
        ))}
      </nav>

      <div className="sidebar-bottom">
        <button type="button" onClick={logout} className="nav-item">
          <LogOut />
          <span>Log out</span>
        </button>
      </div>
    </aside>
  );
};
