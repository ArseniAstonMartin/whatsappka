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
    path: 'forgot-password',
    title: 'Восстановление пароля',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'reset-password',
    title: 'Новый пароль',
    loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'confirm-email',
    title: 'Подтверждение почты',
    loadComponent: () => import('./features/auth/confirm-email/confirm-email').then((m) => m.ConfirmEmail),
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
        loadComponent: () => import('./features/search/search-page/search-page').then((m) => m.SearchPage),
      },
      {
        path: 'hashtags/:tag',
        title: 'Хештег',
        loadComponent: () => import('./features/search/hashtag-page/hashtag-page').then((m) => m.HashtagPage),
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
        path: 'posts/:id',
        title: 'Запись',
        loadComponent: () => import('./features/posts/post-detail/post-detail').then((m) => m.PostDetail),
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
            data: { heading: 'Сообщения', kind: 'empty', title: 'Выберите диалог', message: 'Слева — личные диалоги и групповые чаты.' },
          },
          {
            path: 'new-group',
            title: 'Новый групповой чат',
            loadComponent: () => import('./features/chats/group-chat-create/group-chat-create').then((m) => m.GroupChatCreate),
          },
          {
            path: ':id/settings',
            title: 'Настройки чата',
            loadComponent: () => import('./features/chats/group-chat-settings/group-chat-settings').then((m) => m.GroupChatSettings),
          },
          {
            path: ':id',
            title: 'Диалог',
            loadComponent: () => import('./features/chats/chat-dialog/chat-dialog').then((m) => m.ChatDialog),
          },
        ],
      },
      {
        path: 'notifications',
        title: 'Уведомления',
        loadComponent: () => import('./features/notifications/notifications-page').then((m) => m.NotificationsPage),
      },
      {
        path: 'admin/users',
        title: 'Пользователи',
        canActivate: [requireRoles('ADMIN')],
        loadComponent: () => import('./features/admin/admin-users/admin-users-page').then((m) => m.AdminUsersPage),
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
        path: 'settings/security',
        title: 'Безопасность',
        loadComponent: () => import('./features/settings/security/security-settings').then((m) => m.SecuritySettings),
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
        loadComponent: () => import('./features/moderation/moderation-page').then((m) => m.ModerationPage),
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
