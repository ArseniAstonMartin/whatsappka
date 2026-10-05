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
        loadComponent: () => import('./features/feed/feed-page/feed-page').then((m) => m.FeedPage),
      },
      {
        path: 'search',
        title: 'Поиск',
        loadComponent: section,
        data: { heading: 'Поиск', kind: 'empty', title: 'Поиск пока недоступен', message: 'Введите запрос, чтобы найти людей, записи, группы и хештеги.' },
      },
      {
        path: 'posts/drafts',
        title: 'Черновики',
        loadComponent: () => import('./features/posts/post-drafts/post-drafts').then((m) => m.PostDrafts),
      },
      {
        path: 'posts/new',
        title: 'Новая запись',
        loadComponent: () => import('./features/posts/post-editor/post-editor').then((m) => m.PostEditor),
      },
      {
        path: 'posts/:id/edit',
        title: 'Редактирование записи',
        loadComponent: () => import('./features/posts/post-editor/post-editor').then((m) => m.PostEditor),
      },
      {
        path: 'groups',
        title: 'Группы',
        loadComponent: () => import('./features/communities/community-catalog/community-catalog').then((m) => m.CommunityCatalog),
      },
      {
        path: 'groups/new',
        title: 'Новое сообщество',
        loadComponent: () => import('./features/communities/community-create/community-create').then((m) => m.CommunityCreate),
      },
      {
        path: 'groups/:slug',
        title: 'Сообщество',
        loadComponent: () => import('./features/communities/community-detail/community-detail').then((m) => m.CommunityDetail),
      },
      {
        path: 'chats',
        title: 'Сообщения',
        loadComponent: () => import('./features/chats/chats-layout/chats-layout').then((m) => m.ChatsLayout),
        children: [
          {
            path: '',
            pathMatch: 'full',
            loadComponent: section,
            data: { heading: 'Сообщения', kind: 'empty', title: 'Выберите диалог', message: 'Слева — ваши личные диалоги. Групповые чаты появятся здесь позже.' },
          },
          {
            path: ':id',
            title: 'Диалог',
            loadComponent: () => import('./features/chats/chat-dialog/chat-dialog').then((m) => m.ChatDialog),
          },
        ],
      },
      {
        path: 'profile',
        title: 'Мой профиль',
        loadComponent: () => import('./features/profile/own-profile/own-profile').then((m) => m.OwnProfilePage),
      },
      {
        path: 'users/:username/:list',
        title: 'Связи',
        loadComponent: () => import('./features/social/connections/connections').then((m) => m.Connections),
      },
      {
        path: 'users/:username',
        title: 'Профиль',
        loadComponent: () => import('./features/profile/public-profile/public-profile').then((m) => m.PublicProfilePage),
      },
      {
        path: 'settings',
        title: 'Настройки',
        loadComponent: () => import('./features/settings/settings-page').then((m) => m.SettingsPage),
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
