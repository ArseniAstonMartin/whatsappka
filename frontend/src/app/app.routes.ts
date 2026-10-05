import { Routes } from '@angular/router';
import { requireRoles } from './core/require-roles';
import { authGuard } from './core/auth.guard';

const section = () => import('./shared/section-page/section-page').then((m) => m.SectionPage);

export const routes: Routes = [
  {
    path: 'login',
    title: 'Вход',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    title: 'Регистрация',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./shell/app-shell').then((m) => m.AppShell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'feed' },
      {
        path: 'feed',
        title: 'Лента',
        loadComponent: section,
        data: { heading: 'Лента', kind: 'empty', title: 'Записей пока нет', message: 'Здесь появятся публикации ваших подписок.' },
      },
      {
        path: 'search',
        title: 'Поиск',
        loadComponent: section,
        data: { heading: 'Поиск', kind: 'empty', title: 'Поиск пока недоступен', message: 'Введите запрос, чтобы найти людей, записи, группы и хештеги.' },
      },
      {
        path: 'groups',
        title: 'Группы',
        loadComponent: section,
        data: { heading: 'Группы', kind: 'empty', title: 'Групп пока нет', message: 'Здесь будут каталог сообществ и ваши группы.' },
      },
      {
        path: 'chats',
        title: 'Сообщения',
        loadComponent: section,
        data: { heading: 'Сообщения', kind: 'empty', title: 'Диалогов пока нет', message: 'Здесь появятся ваши личные и групповые чаты.' },
      },
      {
        path: 'profile',
        title: 'Профиль',
        loadComponent: section,
        data: { heading: 'Профиль', kind: 'empty', title: 'Профиль пока пуст', message: 'Здесь появятся обложка, имя, статус и записи.' },
      },
      {
        path: 'settings',
        title: 'Настройки',
        loadComponent: section,
        data: { heading: 'Настройки', kind: 'empty', title: 'Настройки пока пусты', message: 'Здесь появятся сессии, блокировки и уведомления.' },
      },
      {
        path: 'manage',
        title: 'Управление',
        canActivate: [requireRoles('MODERATOR', 'ADMIN')],
        loadComponent: section,
        data: { heading: 'Управление', kind: 'empty', title: 'Очередь пуста', message: 'Здесь появятся жалобы и административные разделы.' },
      },
      {
        path: 'forbidden',
        title: 'Доступ запрещён',
        loadComponent: section,
        data: { heading: 'Доступ запрещён', kind: 'forbidden', title: 'Недостаточно прав', message: 'Этот раздел доступен только модераторам и администраторам.' },
      },
      {
        path: '**',
        title: 'Страница не найдена',
        loadComponent: section,
        data: { heading: 'Страница не найдена', kind: 'not-found', title: 'Такой страницы нет', message: 'Возможно, ссылка устарела или раздел был удалён.' },
      },
    ],
  },
];
