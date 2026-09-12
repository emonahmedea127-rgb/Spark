-- Spark Social Media Supabase Schema Migration
-- Enables extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. PROFILES TABLE
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    name TEXT NOT NULL DEFAULT '',
    username TEXT UNIQUE NOT NULL,
    email TEXT DEFAULT '',
    phone_number TEXT DEFAULT '',
    profile_image_url TEXT DEFAULT '',
    cover_image_url TEXT DEFAULT '',
    bio TEXT DEFAULT '',
    first_name TEXT DEFAULT '',
    last_name TEXT DEFAULT '',
    gender TEXT DEFAULT '',
    date_of_birth TEXT DEFAULT '',
    location TEXT DEFAULT '',
    work TEXT DEFAULT '',
    education TEXT DEFAULT '',
    relationship_status TEXT DEFAULT '',
    is_verified BOOLEAN DEFAULT false,
    is_online BOOLEAN DEFAULT false,
    last_seen TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

-- 2. POSTS TABLE
CREATE TABLE IF NOT EXISTS public.posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    author_name TEXT NOT NULL DEFAULT '',
    author_username TEXT NOT NULL DEFAULT '',
    author_avatar TEXT DEFAULT '',
    is_author_verified BOOLEAN DEFAULT false,
    text TEXT NOT NULL DEFAULT '',
    content TEXT NOT NULL DEFAULT '',
    media_url TEXT DEFAULT '',
    media_urls TEXT[] DEFAULT '{}',
    media_type TEXT DEFAULT 'TEXT',
    feeling TEXT DEFAULT '',
    audience TEXT DEFAULT 'Public',
    author_type TEXT DEFAULT 'PERSONAL',
    target_group_id TEXT,
    target_group_name TEXT,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    share_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    timestamp BIGINT DEFAULT EXTRACT(EPOCH FROM now()) * 1000
);

-- 3. COMMENTS TABLE
CREATE TABLE IF NOT EXISTS public.comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    author_name TEXT NOT NULL DEFAULT '',
    author_avatar TEXT DEFAULT '',
    is_author_verified BOOLEAN DEFAULT false,
    text TEXT NOT NULL DEFAULT '',
    parent_comment_id UUID,
    created_at TIMESTAMPTZ DEFAULT now(),
    timestamp BIGINT DEFAULT EXTRACT(EPOCH FROM now()) * 1000
);

-- 4. POST LIKES TABLE (Unique constraint for post_id + user_id)
CREATE TABLE IF NOT EXISTS public.post_likes (
    post_id UUID NOT NULL REFERENCES public.posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (post_id, user_id)
);

-- 5. FRIEND REQUESTS TABLE
CREATE TABLE IF NOT EXISTS public.friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending', -- 'pending', 'accepted', 'rejected'
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now(),
    UNIQUE(sender_id, receiver_id)
);

-- 6. FRIENDSHIPS TABLE
CREATE TABLE IF NOT EXISTS public.friendships (
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    friend_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (user_id, friend_id)
);

-- 7. FOLLOWS TABLE
CREATE TABLE IF NOT EXISTS public.follows (
    follower_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    following_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY (follower_id, following_id)
);

-- 8. CONVERSATIONS TABLE
CREATE TABLE IF NOT EXISTS public.conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_one UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    participant_two UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    last_message TEXT DEFAULT '',
    last_message_timestamp TIMESTAMPTZ DEFAULT now(),
    created_at TIMESTAMPTZ DEFAULT now()
);

-- 9. MESSAGES TABLE
CREATE TABLE IF NOT EXISTS public.messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    receiver_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    message TEXT NOT NULL DEFAULT '',
    media_url TEXT DEFAULT '',
    message_type TEXT DEFAULT 'TEXT',
    is_seen BOOLEAN DEFAULT false,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    timestamp BIGINT DEFAULT EXTRACT(EPOCH FROM now()) * 1000
);

-- 10. NOTIFICATIONS TABLE
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    actor_id UUID REFERENCES public.profiles(id) ON DELETE SET NULL,
    type TEXT NOT NULL, -- 'like', 'comment', 'friend_request', 'friend_accept', 'follow', 'message'
    entity_id TEXT,
    message TEXT NOT NULL DEFAULT '',
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now()
);

-- ==========================================
-- ROW LEVEL SECURITY (RLS)
-- ==========================================
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.post_likes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.friend_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.friendships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.follows ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

-- Profiles: Public read, self-update and insert
CREATE POLICY "Public profiles are viewable by everyone" ON public.profiles
    FOR SELECT USING (true);

CREATE POLICY "Users can insert their own profile" ON public.profiles
    FOR INSERT WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update their own profile" ON public.profiles
    FOR UPDATE USING (auth.uid() = id);

-- Posts: Public read, author insert/update/delete
CREATE POLICY "Posts viewable by everyone" ON public.posts
    FOR SELECT USING (true);

CREATE POLICY "Authenticated users can create posts" ON public.posts
    FOR INSERT WITH CHECK (auth.uid() = author_id);

CREATE POLICY "Authors can update own posts" ON public.posts
    FOR UPDATE USING (auth.uid() = author_id);

CREATE POLICY "Authors can delete own posts" ON public.posts
    FOR DELETE USING (auth.uid() = author_id);

-- Comments: Public read, author insert/delete
CREATE POLICY "Comments viewable by everyone" ON public.comments
    FOR SELECT USING (true);

CREATE POLICY "Authenticated users can create comments" ON public.comments
    FOR INSERT WITH CHECK (auth.uid() = author_id);

CREATE POLICY "Authors can delete own comments" ON public.comments
    FOR DELETE USING (auth.uid() = author_id);

-- Post Likes: Viewable by everyone, user can like/unlike
CREATE POLICY "Likes viewable by everyone" ON public.post_likes
    FOR SELECT USING (true);

CREATE POLICY "Users can like posts" ON public.post_likes
    FOR INSERT WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can remove their like" ON public.post_likes
    FOR DELETE USING (auth.uid() = user_id);

-- Friend Requests: Participants can view/manage
CREATE POLICY "Users can view their friend requests" ON public.friend_requests
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

CREATE POLICY "Users can send friend requests" ON public.friend_requests
    FOR INSERT WITH CHECK (auth.uid() = sender_id);

CREATE POLICY "Users can update friend requests received" ON public.friend_requests
    FOR UPDATE USING (auth.uid() = receiver_id OR auth.uid() = sender_id);

CREATE POLICY "Users can cancel friend requests" ON public.friend_requests
    FOR DELETE USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Friendships: Viewable by everyone, manage own friendships
CREATE POLICY "Friendships viewable by everyone" ON public.friendships
    FOR SELECT USING (true);

CREATE POLICY "Users can manage friendships" ON public.friendships
    FOR ALL USING (auth.uid() = user_id OR auth.uid() = friend_id);

-- Follows: Viewable by everyone, manage own follows
CREATE POLICY "Follows viewable by everyone" ON public.follows
    FOR SELECT USING (true);

CREATE POLICY "Users can follow others" ON public.follows
    FOR INSERT WITH CHECK (auth.uid() = follower_id);

CREATE POLICY "Users can unfollow others" ON public.follows
    FOR DELETE USING (auth.uid() = follower_id);

-- Conversations: Only participants can view
CREATE POLICY "Conversation participants can view conversations" ON public.conversations
    FOR SELECT USING (auth.uid() = participant_one OR auth.uid() = participant_two);

CREATE POLICY "Participants can insert conversations" ON public.conversations
    FOR INSERT WITH CHECK (auth.uid() = participant_one OR auth.uid() = participant_two);

-- Messages: Only conversation participants can view and send
CREATE POLICY "Conversation participants can view messages" ON public.messages
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

CREATE POLICY "Sender can create messages" ON public.messages
    FOR INSERT WITH CHECK (auth.uid() = sender_id);

-- Notifications: Only recipient can view
CREATE POLICY "Users can view own notifications" ON public.notifications
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Actors can insert notifications" ON public.notifications
    FOR INSERT WITH CHECK (auth.uid() = actor_id);

CREATE POLICY "Users can update own notifications" ON public.notifications
    FOR UPDATE USING (auth.uid() = user_id);

-- ==========================================
-- STORAGE BUCKETS CONFIGURATION
-- ==========================================
INSERT INTO storage.buckets (id, name, public) VALUES 
    ('avatars', 'avatars', true),
    ('covers', 'covers', true),
    ('post-media', 'post-media', true),
    ('reels', 'reels', true)
ON CONFLICT (id) DO NOTHING;

-- Storage RLS Policies
CREATE POLICY "Public avatar access" ON storage.objects
    FOR SELECT USING (bucket_id = 'avatars');

CREATE POLICY "Users can upload avatar" ON storage.objects
    FOR INSERT WITH CHECK (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Public cover access" ON storage.objects
    FOR SELECT USING (bucket_id = 'covers');

CREATE POLICY "Users can upload cover" ON storage.objects
    FOR INSERT WITH CHECK (bucket_id = 'covers' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Public post media access" ON storage.objects
    FOR SELECT USING (bucket_id = 'post-media');

CREATE POLICY "Users can upload post media" ON storage.objects
    FOR INSERT WITH CHECK (bucket_id = 'post-media' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Public reels access" ON storage.objects
    FOR SELECT USING (bucket_id = 'reels');

CREATE POLICY "Users can upload reels" ON storage.objects
    FOR INSERT WITH CHECK (bucket_id = 'reels' AND auth.uid()::text = (storage.foldername(name))[1]);

-- Storage Update/Delete Policies
CREATE POLICY "Users can update own avatar" ON storage.objects
    FOR UPDATE USING (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can delete own avatar" ON storage.objects
    FOR DELETE USING (bucket_id = 'avatars' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Users can update own cover" ON storage.objects
    FOR UPDATE USING (bucket_id = 'covers' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can delete own cover" ON storage.objects
    FOR DELETE USING (bucket_id = 'covers' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Users can update own post media" ON storage.objects
    FOR UPDATE USING (bucket_id = 'post-media' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can delete own post media" ON storage.objects
    FOR DELETE USING (bucket_id = 'post-media' AND auth.uid()::text = (storage.foldername(name))[1]);

CREATE POLICY "Users can update own reels" ON storage.objects
    FOR UPDATE USING (bucket_id = 'reels' AND auth.uid()::text = (storage.foldername(name))[1]);
CREATE POLICY "Users can delete own reels" ON storage.objects
    FOR DELETE USING (bucket_id = 'reels' AND auth.uid()::text = (storage.foldername(name))[1]);

-- ==========================================
-- REALTIME PUBLICATION & REPLICA IDENTITY
-- ==========================================
-- Ensure full rows are sent with update and delete events
ALTER TABLE public.profiles REPLICA IDENTITY FULL;
ALTER TABLE public.posts REPLICA IDENTITY FULL;
ALTER TABLE public.comments REPLICA IDENTITY FULL;
ALTER TABLE public.post_likes REPLICA IDENTITY FULL;
ALTER TABLE public.messages REPLICA IDENTITY FULL;
ALTER TABLE public.notifications REPLICA IDENTITY FULL;
ALTER TABLE public.friend_requests REPLICA IDENTITY FULL;
ALTER TABLE public.friendships REPLICA IDENTITY FULL;
ALTER TABLE public.follows REPLICA IDENTITY FULL;

-- Add tables to realtime publication
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.profiles;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.posts;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.comments;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.post_likes;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.friend_requests;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.friendships;
        ALTER PUBLICATION supabase_realtime ADD TABLE public.follows;
    END IF;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

-- ==========================================
-- AUTH PROFILE SYNCHRONIZATION TRIGGER
-- ==========================================
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, username, email, name, first_name, last_name)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data->>'username', split_part(NEW.email, '@', 1)),
    COALESCE(NEW.email, ''),
    COALESCE(NEW.raw_user_meta_data->>'name', NEW.raw_user_meta_data->>'full_name', ''),
    COALESCE(NEW.raw_user_meta_data->>'first_name', ''),
    COALESCE(NEW.raw_user_meta_data->>'last_name', '')
  )
  ON CONFLICT (id) DO UPDATE SET
    email = EXCLUDED.email,
    updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

